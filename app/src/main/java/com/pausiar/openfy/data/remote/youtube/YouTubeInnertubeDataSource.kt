package com.pausiar.openfy.data.remote.youtube

import com.pausiar.openfy.data.remote.RemotePlaylistPayload
import com.pausiar.openfy.data.remote.RemoteTrackPayload
import com.pausiar.openfy.domain.models.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Importacion de YouTube sin API key usando los endpoints publicos "innertube".
 * Soporta paginacion por continuation (playlists de mas de 100 videos).
 */
class YouTubeInnertubeDataSource(
    httpClient: OkHttpClient,
) {
    // Cliente derivado con timeouts acotados para que un import nunca se quede colgado.
    private val httpClient: OkHttpClient = httpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedInnertubeApiKey: String? = null

    suspend fun fetchPlaylist(playlistId: String, originalUrl: String): RemotePlaylistPayload? =
        withContext(Dispatchers.IO) {
            val apiKey = resolveInnertubeApiKey()

            var firstPage: JSONObject? = postBrowse(apiKey, JSONObject().put("browseId", "VL$playlistId"))
            var entries = firstPage?.let { extractEntries(it) }.orEmpty()

            if (firstPage == null || entries.isEmpty()) {
                // Plan B: la pagina HTML de la playlist incluye ytInitialData con el mismo contenido.
                val htmlData = fetchInitialDataFromPlaylistPage(playlistId)
                if (htmlData != null) {
                    val htmlEntries = extractEntries(htmlData)
                    if (htmlEntries.isNotEmpty()) {
                        firstPage = htmlData
                        entries = htmlEntries
                    }
                }
            }
            val page = firstPage ?: return@withContext null
            if (entries.isEmpty()) return@withContext null

            val all = LinkedHashMap<String, VideoEntry>()
            entries.forEach { all.putIfAbsent(it.videoId, it) }

            var token = findContinuationToken(page)
            var pages = 0
            while (token != null && pages < MAX_CONTINUATIONS && all.size < MAX_ITEMS) {
                coroutineContext.ensureActive()
                val next = postBrowse(apiKey, JSONObject().put("continuation", token)) ?: break
                val more = extractEntries(next)
                if (more.isEmpty()) break
                more.forEach { all.putIfAbsent(it.videoId, it) }
                val newToken = findContinuationToken(next)
                if (newToken == token) break
                token = newToken
                pages++
            }

            buildPayload(extractPlaylistTitle(page), originalUrl, all.values.toList())
        }

    suspend fun fetchVideo(videoId: String, originalUrl: String): RemotePlaylistPayload? =
        withContext(Dispatchers.IO) {
            val meta = fetchOEmbed(videoId)
            val title = meta?.first ?: "Vídeo de YouTube"
            val artist = meta?.second?.removeSuffix(" - Topic")?.takeIf { it.isNotBlank() } ?: "YouTube"
            val thumb = thumbnailFor(videoId)
            RemotePlaylistPayload(
                title = title,
                platform = Platform.YOUTUBE,
                originalUrl = originalUrl,
                imageUrl = thumb,
                description = "Importado desde YouTube.",
                tracks = listOf(
                    RemoteTrackPayload(
                        title = title,
                        artist = artist,
                        album = null,
                        durationMs = null,
                        imageUrl = thumb,
                        originalUrl = "https://www.youtube.com/watch?v=$videoId",
                        platform = Platform.YOUTUBE,
                        isPlayableInApp = true,
                    )
                ),
            )
        }

    private fun buildPayload(title: String, originalUrl: String, entries: List<VideoEntry>): RemotePlaylistPayload =
        RemotePlaylistPayload(
            title = title,
            platform = Platform.YOUTUBE,
            originalUrl = originalUrl,
            imageUrl = entries.firstOrNull()?.imageUrl,
            description = "Importada desde YouTube.",
            tracks = entries.map { entry ->
                RemoteTrackPayload(
                    title = entry.title,
                    artist = entry.channel ?: "YouTube",
                    album = null,
                    durationMs = entry.durationMs,
                    imageUrl = entry.imageUrl,
                    originalUrl = "https://www.youtube.com/watch?v=${entry.videoId}",
                    platform = Platform.YOUTUBE,
                    isPlayableInApp = true,
                )
            },
        )

    // ---- Red ----

    /** POST a /browse. Devuelve null ante cualquier fallo de red/HTTP/JSON (se prueba el plan B). */
    private fun postBrowse(apiKey: String, extra: JSONObject): JSONObject? {
        val body = JSONObject()
            .put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject()
                        .put("clientName", "WEB")
                        .put("clientVersion", CLIENT_VERSION)
                        .put("hl", "es")
                        .put("gl", "ES"),
                ),
            )
        extra.keys().forEach { key -> body.put(key, extra.get(key)) }

        val url = if (apiKey.isNotBlank()) "$INNERTUBE_BROWSE_URL?key=$apiKey" else INNERTUBE_BROWSE_URL
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "es-ES,es;q=0.9")
            .header("Cookie", CONSENT_COOKIE)
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val text = response.body?.string().orEmpty()
                if (text.isBlank()) null else JSONObject(text)
            }
        } catch (ignored: IOException) {
            null
        } catch (ignored: JSONException) {
            null
        }
    }

    private fun fetchInitialDataFromPlaylistPage(playlistId: String): JSONObject? {
        val request = Request.Builder()
            .url("https://www.youtube.com/playlist?list=$playlistId&hl=es")
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "es-ES,es;q=0.9")
            .header("Cookie", CONSENT_COOKIE)
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val html = response.body?.string().orEmpty()
                if (html.isBlank()) null else extractInitialData(html)
            }
        } catch (ignored: IOException) {
            null
        } catch (ignored: JSONException) {
            null
        }
    }

    /** Devuelve (titulo, autor) del oEmbed, o null si no esta disponible (privado, sin embed...). */
    private fun fetchOEmbed(videoId: String): Pair<String, String?>? {
        val url = "https://www.youtube.com/oembed".toHttpUrl().newBuilder()
            .addQueryParameter("url", "https://www.youtube.com/watch?v=$videoId")
            .addQueryParameter("format", "json")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        // Los IOException (sin red) se propagan: el repositorio los traduce a un mensaje claro.
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                null
            } else {
                parseOEmbed(response.body?.string().orEmpty())
            }
        }
    }

    private fun parseOEmbed(json: String): Pair<String, String?>? = try {
        val obj = JSONObject(json)
        val title = obj.optString("title")
        if (title.isBlank()) null else title to obj.optString("author_name").takeIf { it.isNotBlank() }
    } catch (ignored: JSONException) {
        null
    }

    private fun resolveInnertubeApiKey(): String {
        cachedInnertubeApiKey?.let { if (it.isNotBlank()) return it }

        val request = Request.Builder()
            .url("https://www.youtube.com/?hl=es")
            .header("User-Agent", USER_AGENT)
            .header("Cookie", CONSENT_COOKIE)
            .build()

        val scraped = try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    ""
                } else {
                    val html = response.body?.string().orEmpty()
                    INNERTUBE_KEY_REGEX.find(html)?.groupValues?.get(1).orEmpty()
                }
            }
        } catch (ignored: IOException) {
            ""
        }

        val key = scraped.ifBlank { FALLBACK_INNERTUBE_KEY }
        // Solo se cachea una clave obtenida de verdad.
        if (scraped.isNotBlank()) cachedInnertubeApiKey = key
        return key
    }

    // ---- Parseo ----

    private fun extractInitialData(html: String): JSONObject? {
        val marker = html.indexOf("ytInitialData")
        if (marker < 0) return null
        val start = html.indexOf('{', marker)
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escaped = false
        var i = start
        while (i < html.length) {
            val c = html[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            return try {
                                JSONObject(html.substring(start, i + 1))
                            } catch (ignored: JSONException) {
                                null
                            }
                        }
                    }
                }
            }
            i++
        }
        return null
    }

    private fun extractPlaylistTitle(root: JSONObject): String {
        val candidates = listOf(
            root.optJSONObject("metadata")?.optJSONObject("playlistMetadataRenderer")?.optString("title"),
            textOf(root.optJSONObject("header")?.optJSONObject("playlistHeaderRenderer")?.optJSONObject("title")),
            root.optJSONObject("header")?.optJSONObject("pageHeaderRenderer")?.optString("pageTitle"),
        )
        return candidates.firstOrNull { !it.isNullOrBlank() }?.trim() ?: "Playlist de YouTube"
    }

    private fun extractEntries(root: JSONObject): List<VideoEntry> {
        val out = ArrayList<VideoEntry>()
        walk(root) { key, value ->
            if ((key == "playlistVideoRenderer" || key == "videoRenderer") && value is JSONObject) {
                parseRenderer(value)?.let(out::add)
            }
            false
        }
        return out
    }

    private fun findContinuationToken(root: JSONObject): String? {
        var token: String? = null
        walk(root) { key, value ->
            if (key == "continuationItemRenderer" && value is JSONObject) {
                val candidate = value.optJSONObject("continuationEndpoint")
                    ?.optJSONObject("continuationCommand")
                    ?.optString("token")
                if (!candidate.isNullOrBlank()) {
                    token = candidate
                    return@walk true
                }
            }
            false
        }
        return token
    }

    /**
     * Recorre el JSON en profundidad. `visit` recibe (clave, valor) de cada campo de objeto y
     * devuelve true para cortar el recorrido. No desciende dentro de un renderer ya procesado.
     */
    private fun walk(node: Any?, visit: (String, Any?) -> Boolean): Boolean {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = node.opt(key)
                    if (visit(key, value)) return true
                    if (key == "playlistVideoRenderer" || key == "videoRenderer") continue
                    if ((value is JSONObject || value is JSONArray) && walk(value, visit)) return true
                }
            }

            is JSONArray -> {
                for (index in 0 until node.length()) {
                    val value = node.opt(index)
                    if ((value is JSONObject || value is JSONArray) && walk(value, visit)) return true
                }
            }
        }
        return false
    }

    private fun parseRenderer(renderer: JSONObject): VideoEntry? {
        val videoId = renderer.optString("videoId")
        if (!VIDEO_ID_REGEX.matches(videoId)) return null
        if (!renderer.optBoolean("isPlayable", true)) return null

        val title = textOf(renderer.optJSONObject("title"))?.trim()
        if (title.isNullOrBlank() || UNAVAILABLE_TITLE_REGEX.matches(title)) return null

        val channel = textOf(renderer.optJSONObject("shortBylineText"))
            ?: textOf(renderer.optJSONObject("longBylineText"))
            ?: textOf(renderer.optJSONObject("ownerText"))

        val durationMs = renderer.optString("lengthSeconds").toLongOrNull()?.takeIf { it > 0 }?.times(1000)
            ?: textOf(renderer.optJSONObject("lengthText"))?.let(::parseDurationLabel)

        return VideoEntry(
            videoId = videoId,
            title = title,
            channel = channel?.removeSuffix(" - Topic")?.trim()?.takeIf { it.isNotBlank() },
            durationMs = durationMs,
            imageUrl = thumbnailFor(videoId),
        )
    }

    private fun textOf(node: JSONObject?): String? {
        if (node == null) return null
        node.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
        val runs = node.optJSONArray("runs") ?: return null
        val builder = StringBuilder()
        for (index in 0 until runs.length()) {
            builder.append(runs.optJSONObject(index)?.optString("text").orEmpty())
        }
        return builder.toString().takeIf { it.isNotBlank() }
    }

    private fun parseDurationLabel(raw: String): Long? {
        val parts = raw.trim().split(":").map { it.trim().toLongOrNull() ?: return null }
        val totalSeconds = when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> return null
        }
        return totalSeconds * 1000
    }

    private fun thumbnailFor(videoId: String) = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

    private data class VideoEntry(
        val videoId: String,
        val title: String,
        val channel: String?,
        val durationMs: Long?,
        val imageUrl: String?,
    )

    private companion object {
        const val INNERTUBE_BROWSE_URL = "https://www.youtube.com/youtubei/v1/browse"
        const val CLIENT_VERSION = "2.20250310.01.00"
        const val MAX_CONTINUATIONS = 100
        const val MAX_ITEMS = 5_000

        // Clave publica del cliente web de YouTube (la misma que sirve youtube.com a cualquier visitante).
        const val FALLBACK_INNERTUBE_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
        const val CONSENT_COOKIE = "SOCS=CAI; CONSENT=YES+1"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val VIDEO_ID_REGEX = Regex("[a-zA-Z0-9_-]{11}")
        val INNERTUBE_KEY_REGEX = Regex(""""INNERTUBE_API_KEY"\s*:\s*"([^"]+)"""")
        val UNAVAILABLE_TITLE_REGEX = Regex(
            """\[?\s*(v[ií]deo|video)\s+(privado|eliminado|no disponible|private|deleted|unavailable)\s*]?""",
            RegexOption.IGNORE_CASE,
        )
    }
}
