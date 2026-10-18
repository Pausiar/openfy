package com.pausiar.openfy.playback

import android.net.Uri
import com.pausiar.openfy.domain.models.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/** Error al obtener la URL real de un stream. No merece reintento automatico de ExoPlayer. */
class StreamResolutionException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/** URL reproducible ya resuelta, con su caducidad y las cabeceras HTTP que necesita. */
data class ResolvedStream(
    val url: String,
    val expiresAtMs: Long,
    val headers: Map<String, String>,
) {
    fun isFresh(nowMs: Long = System.currentTimeMillis()): Boolean = nowMs < expiresAtMs
}

/**
 * Resuelve las URIs `openfy-yt://` y `openfy-search://` (ver [PlaybackUris]) a URLs reales.
 *
 * - Cache en memoria videoId -> URL (con caducidad segun el parametro `expire`).
 * - Deduplica resoluciones concurrentes del mismo video (prefetch + loader de ExoPlayer).
 * - NewPipe primero; si tarda, se lanzan en paralelo las instancias de Piped y gana la primera
 *   respuesta valida. El API innertube queda como ultimo recurso.
 *
 * Es seguro llamarlo desde cualquier hilo; [resolveUri] es bloqueante (pensado para el hilo de
 * carga de ExoPlayer) y [prefetch] no bloquea.
 */
class YouTubeStreamResolver(
    private val httpClient: OkHttpClient,
    private val pipedStreamDataSource: PipedStreamDataSource,
) {
    private val streamCache = ConcurrentHashMap<String, ResolvedStream>()
    private val searchCache = ConcurrentHashMap<String, String>()
    private val searchFailureUntil = ConcurrentHashMap<String, Long>()
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<ResolvedStream>>()
    private val executor: ExecutorService = Executors.newCachedThreadPool(ResolverThreadFactory())

    private val apiKeyLock = Any()

    @Volatile
    private var cachedInnertubeApiKey: String? = null

    /** Compatibilidad: devuelve una URL directamente reproducible para [track], o null. */
    suspend fun resolvePlaybackUri(track: Track): String? = withContext(Dispatchers.IO) {
        val uri = PlaybackUris.forTrack(track) ?: return@withContext null
        runCatching { resolveUri(Uri.parse(uri))?.url ?: uri }.getOrNull()
    }

    /**
     * Resuelve [uri] de forma bloqueante. Devuelve null si [uri] no es una URI propia de Openfy
     * (contenido local, previews http...), y lanza [IOException] si no se pudo resolver.
     */
    @Throws(IOException::class)
    fun resolveUri(uri: Uri): ResolvedStream? {
        return when (val target = PlaybackUris.parse(uri)) {
            is PlaybackUris.Target.Video -> resolveVideo(target.videoId)
            is PlaybackUris.Target.Search -> resolveSearch(target)
            null -> {
                if (PlaybackUris.isCustom(uri)) {
                    throw StreamResolutionException("URI de reproducción no válida: $uri")
                }
                null
            }
        }
    }

    /** Lanza en segundo plano la resolucion de [uri] (si hace falta) sin bloquear. */
    fun prefetch(uri: Uri?) {
        when (val target = PlaybackUris.parse(uri)) {
            is PlaybackUris.Target.Video -> {
                if (freshCached(target.videoId) == null) videoFuture(target.videoId)
            }
            is PlaybackUris.Target.Search -> {
                val videoId = searchCache[searchKey(target.query)]
                if (videoId == null || freshCached(videoId) == null) searchFuture(target)
            }
            null -> Unit
        }
    }

    /** Olvida la URL cacheada para [uri] (p. ej. tras un 403 de googlevideo). */
    fun invalidate(uri: Uri?) {
        when (val target = PlaybackUris.parse(uri)) {
            is PlaybackUris.Target.Video -> streamCache.remove(target.videoId)
            is PlaybackUris.Target.Search -> {
                searchCache[searchKey(target.query)]?.let { streamCache.remove(it) }
            }
            null -> Unit
        }
    }

    // region Video

    private fun resolveVideo(videoId: String): ResolvedStream {
        freshCached(videoId)?.let { return it }
        return await(videoFuture(videoId))
    }

    private fun videoFuture(videoId: String): CompletableFuture<ResolvedStream> =
        startOnce("video:$videoId") {
            freshCached(videoId) ?: resolveVideoUncached(videoId).also { streamCache[videoId] = it }
        }

    private fun freshCached(videoId: String): ResolvedStream? {
        val cached = streamCache[videoId] ?: return null
        if (cached.isFresh()) return cached
        streamCache.remove(videoId, cached)
        return null
    }

    private fun resolveVideoUncached(videoId: String): ResolvedStream {
        val newPipe: () -> String? = { resolveWithNewPipe(videoId) }
        val piped: List<() -> String?> = pipedStreamDataSource.endpoints.map { baseUrl ->
            { pipedStreamDataSource.resolveFromEndpoint(baseUrl, videoId) }
        }
        val url = race(primary = newPipe, hedges = piped)
            ?: runCatching { resolveWithInnertubePlayer(videoId) }.getOrNull()
            ?: throw StreamResolutionException("No se ha encontrado un stream reproducible para $videoId")
        return ResolvedStream(
            url = url,
            expiresAtMs = YouTubeHttp.expiryFor(url),
            headers = YouTubeHttp.playbackHeadersFor(url),
        )
    }

    // endregion

    // region Search

    private fun resolveSearch(target: PlaybackUris.Target.Search): ResolvedStream {
        searchCache[searchKey(target.query)]?.let { videoId ->
            freshCached(videoId)?.let { return it }
        }
        return await(searchFuture(target))
    }

    private fun searchFuture(target: PlaybackUris.Target.Search): CompletableFuture<ResolvedStream> =
        startOnce("search:${searchKey(target.query)}") { resolveSearchUncached(target) }

    private fun resolveSearchUncached(target: PlaybackUris.Target.Search): ResolvedStream {
        val key = searchKey(target.query)
        val now = System.currentTimeMillis()
        // Si fallo hace poco y hay preview, se usa directamente sin repetir la busqueda.
        val recentlyFailed = (searchFailureUntil[key] ?: 0L) > now && target.fallbackUrl != null

        val videoId = if (recentlyFailed) {
            null
        } else {
            searchCache[key]
                ?: runCatching { searchVideoId(target.query) }.getOrNull()?.also { searchCache[key] = it }
        }

        if (videoId != null) {
            val resolved = runCatching { await(videoFuture(videoId)) }.getOrNull()
            if (resolved != null) return resolved
        }

        searchFailureUntil[key] = now + SEARCH_FAILURE_TTL_MS
        val fallback = target.fallbackUrl
            ?: throw StreamResolutionException("No se ha encontrado \"${target.query}\" en YouTube")
        return ResolvedStream(
            url = fallback,
            expiresAtMs = now + SEARCH_FAILURE_TTL_MS,
            headers = YouTubeHttp.playbackHeadersFor(fallback),
        )
    }

    private fun searchVideoId(query: String): String? {
        val extractor = ServiceList.YouTube.getSearchExtractor(query)
        extractor.fetchPage()
        return extractor.initialPage.items
            .asSequence()
            .filterIsInstance<StreamInfoItem>()
            .filter { item ->
                val type = item.streamType
                type != StreamType.LIVE_STREAM && type != StreamType.AUDIO_LIVE_STREAM
            }
            .mapNotNull { item -> YouTubeHttp.extractVideoId(item.url) }
            .firstOrNull()
    }

    private fun searchKey(query: String): String = query.trim().lowercase()

    // endregion

    // region Estrategias

    private fun resolveWithNewPipe(videoId: String): String? {
        val extractor = ServiceList.YouTube.getStreamExtractor(YouTubeHttp.watchUrl(videoId))
        extractor.fetchPage()

        val audio = runCatching { extractor.audioStreams }.getOrNull().orEmpty()
            .filter { it.isProgressiveUrl() }
        pickAudio(audio)?.content?.takeIf { it.isNotBlank() }?.let { return it }

        return runCatching { extractor.videoStreams }.getOrNull().orEmpty()
            .filter { !it.isVideoOnly && it.isProgressiveUrl() }
            .maxByOrNull { it.bitrate }
            ?.content
            ?.takeIf { it.isNotBlank() }
    }

    private fun Stream.isProgressiveUrl(): Boolean =
        isUrl && deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && !content.isNullOrBlank()

    /** Pista de audio original (no doblada) con el mayor bitrate. */
    private fun pickAudio(streams: List<AudioStream>): AudioStream? =
        streams.maxWithOrNull(
            compareBy<AudioStream>(
                { stream ->
                    val type = stream.audioTrackType
                    type == null || type == AudioTrackType.ORIGINAL
                },
                { stream -> stream.averageBitrate },
            )
        )

    private fun resolveWithInnertubePlayer(videoId: String): String? {
        val apiKey = fetchInnertubeApiKey()
        if (apiKey.isBlank()) return null

        val body = JSONObject()
            .put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject()
                        .put("clientName", "ANDROID")
                        .put("clientVersion", "19.09.37")
                        .put("androidSdkVersion", 33)
                        .put("hl", "es")
                        .put("gl", "ES"),
                ),
            )
            .put("videoId", videoId)
            .toString()

        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?key=$apiKey")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("User-Agent", YouTubeHttp.USER_AGENT)
            .header("Content-Type", "application/json")
            .build()

        val response = runCatching { httpClient.newCall(request).execute() }.getOrNull() ?: return null
        return response.use { httpResponse ->
            if (!httpResponse.isSuccessful) return null
            val json = httpResponse.body?.string().orEmpty()
            if (json.isBlank()) return null
            extractStreamUrlFromPlayerResponse(json)
        }
    }

    private fun extractStreamUrlFromPlayerResponse(json: String): String? {
        val directUrl = Regex(""""url"\s*:\s*"(https://[^"\\]+)""")
            .findAll(json)
            .map { it.groupValues[1].replace("\\u0026", "&") }
            .firstOrNull { url ->
                url.contains("googlevideo.com") || url.contains("mime=audio")
            }
        if (directUrl != null) return directUrl

        val ciphered = Regex(""""signatureCipher"\s*:\s*"([^"]+)""")
            .find(json)
            ?.groupValues
            ?.get(1)
            ?: return null

        val params = ciphered.split("&").associate { part ->
            val pieces = part.split("=", limit = 2)
            pieces[0] to pieces.getOrNull(1).orEmpty()
        }
        val baseUrl = params["url"]?.replace("\\u0026", "&") ?: return null
        val signature = params["sig"] ?: params["s"] ?: return null
        return if (baseUrl.contains("?")) "$baseUrl&sig=$signature" else "$baseUrl?sig=$signature"
    }

    private fun fetchInnertubeApiKey(): String {
        cachedInnertubeApiKey?.let { if (it.isNotBlank()) return it }

        synchronized(apiKeyLock) {
            cachedInnertubeApiKey?.let { if (it.isNotBlank()) return it }

            val request = Request.Builder()
                .url("https://www.youtube.com")
                .header("User-Agent", YouTubeHttp.USER_AGENT)
                .build()

            val key = runCatching {
                httpClient.newCall(request).execute().use { httpResponse ->
                    if (!httpResponse.isSuccessful) return@runCatching ""
                    val html = httpResponse.body?.string().orEmpty()
                    Regex(""""INNERTUBE_API_KEY"\s*:\s*"([^"]+)"""")
                        .find(html)
                        ?.groupValues
                        ?.get(1)
                        .orEmpty()
                }
            }.getOrDefault("")

            // Solo se cachea una clave valida, para poder reintentar si la peticion fallo.
            if (key.isNotBlank()) cachedInnertubeApiKey = key
            return key
        }
    }

    // endregion

    // region Concurrencia

    /**
     * Ejecuta [primary]; si no ha respondido en [HEDGE_DELAY_MS] (o falla), lanza tambien
     * [hedges] en paralelo. Devuelve el primer resultado no vacio, o null.
     */
    private fun race(primary: () -> String?, hedges: List<() -> String?>): String? {
        val completion = ExecutorCompletionService<String?>(executor)
        val futures = mutableListOf<Future<String?>>()

        fun submit(task: () -> String?) {
            futures += completion.submit(Callable<String?> { runCatching { task() }.getOrNull() })
        }

        try {
            val startNs = System.nanoTime()
            val hedgeAtNs = startNs + TimeUnit.MILLISECONDS.toNanos(HEDGE_DELAY_MS)
            val deadlineNs = startNs + TimeUnit.MILLISECONDS.toNanos(RACE_TIMEOUT_MS)

            submit(primary)
            var pending = 1
            var hedged = hedges.isEmpty()

            while (pending > 0 || !hedged) {
                val nowNs = System.nanoTime()
                if (!hedged && (pending == 0 || nowNs >= hedgeAtNs)) {
                    hedges.forEach { submit(it) }
                    pending += hedges.size
                    hedged = true
                    continue
                }

                val waitUntilNs = if (hedged) deadlineNs else minOf(hedgeAtNs, deadlineNs)
                val remainingNs = waitUntilNs - nowNs
                if (remainingNs <= 0L) {
                    if (hedged) return null
                    continue
                }

                val done = completion.poll(remainingNs, TimeUnit.NANOSECONDS) ?: continue
                pending--
                val value = runCatching { done.get() }.getOrNull()
                if (!value.isNullOrBlank()) return value
            }
            return null
        } finally {
            futures.forEach { it.cancel(true) }
        }
    }

    /** Arranca [block] en segundo plano salvo que ya haya una resolucion en curso para [key]. */
    private fun startOnce(key: String, block: () -> ResolvedStream): CompletableFuture<ResolvedStream> {
        inFlight[key]?.let { return it }
        val future = CompletableFuture<ResolvedStream>()
        inFlight.putIfAbsent(key, future)?.let { return it }

        try {
            executor.execute {
                try {
                    future.complete(block())
                } catch (error: Throwable) {
                    future.completeExceptionally(error)
                } finally {
                    inFlight.remove(key, future)
                }
            }
        } catch (error: RejectedExecutionException) {
            inFlight.remove(key, future)
            future.completeExceptionally(error)
        }
        return future
    }

    private fun await(future: Future<ResolvedStream>): ResolvedStream {
        try {
            return future.get(AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (error: ExecutionException) {
            val cause = error.cause
            if (cause is IOException) throw cause
            throw StreamResolutionException("No se ha podido obtener el audio", cause)
        } catch (error: TimeoutException) {
            throw StreamResolutionException("Tiempo de espera agotado al obtener el audio", error)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Resolución interrumpida")
        }
    }

    private class ResolverThreadFactory : ThreadFactory {
        private val counter = AtomicInteger(0)

        override fun newThread(runnable: Runnable): Thread =
            Thread(runnable, "openfy-resolver-${counter.incrementAndGet()}").apply { isDaemon = true }
    }

    // endregion

    private companion object {
        const val HEDGE_DELAY_MS = 2_500L
        const val RACE_TIMEOUT_MS = 15_000L
        const val AWAIT_TIMEOUT_MS = 45_000L
        const val SEARCH_FAILURE_TTL_MS = 10L * 60L * 1000L
    }
}
