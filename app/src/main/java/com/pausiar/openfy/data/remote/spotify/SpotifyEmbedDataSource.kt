package com.pausiar.openfy.data.remote.spotify

import com.pausiar.openfy.data.remote.RemotePlaylistPayload
import com.pausiar.openfy.data.remote.RemoteTrackPayload
import com.pausiar.openfy.domain.models.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/**
 * Error de importacion de Spotify con un mensaje ya preparado para mostrar al usuario.
 */
class SpotifyImportException(message: String) : Exception(message)

/**
 * Importacion de Spotify sin proxy ni credenciales: lee la pagina publica de "embed"
 * (open.spotify.com/embed/{playlist|album|track}/{id}), que incluye los metadatos en un
 * bloque JSON `__NEXT_DATA__`.
 *
 * El embed solo trae las primeras ~100 canciones. Si la pagina incluye un token anonimo se
 * intenta paginar la Web API publica para traer el resto; si falla, se conserva lo leido.
 *
 * Spotify cambia este formato sin aviso, asi que todo el parseo es defensivo.
 */
class SpotifyEmbedDataSource(
    httpClient: OkHttpClient,
) {
    // Cliente derivado con timeouts acotados para que un import nunca se quede colgado.
    private val httpClient: OkHttpClient = httpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun fetchPlaylist(playlistId: String, originalUrl: String): RemotePlaylistPayload =
        withContext(Dispatchers.IO) {
            val root = fetchEmbedJson(TYPE_PLAYLIST, playlistId)
            val entity = findCollectionEntity(root)
                ?: throw SpotifyImportException(UNREADABLE_MESSAGE)

            val title = entity.text("name") ?: entity.text("title") ?: "Playlist de Spotify"
            val owner = entity.text("subtitle")
            val cover = findCover(entity)
            val embedTracks = parseTrackList(entity.optJSONArray("trackList"), album = null, imageUrl = cover)

            val token = findAccessToken(root)
            val apiTracks = if (token != null && embedTracks.size >= EMBED_TRUNCATION_THRESHOLD) {
                fetchPlaylistTracksFromApi(playlistId, token, cover)
            } else {
                emptyList()
            }
            val tracks = finalizeTracks(embedTracks, apiTracks)
            if (tracks.isEmpty()) throw SpotifyImportException(UNREADABLE_MESSAGE)

            RemotePlaylistPayload(
                title = title,
                platform = Platform.SPOTIFY,
                originalUrl = originalUrl,
                imageUrl = cover,
                description = owner?.let { "Playlist de Spotify de $it." } ?: "Importada desde Spotify.",
                tracks = tracks,
            )
        }

    suspend fun fetchAlbum(albumId: String, originalUrl: String): RemotePlaylistPayload =
        withContext(Dispatchers.IO) {
            val root = fetchEmbedJson(TYPE_ALBUM, albumId)
            val entity = findCollectionEntity(root)
                ?: throw SpotifyImportException(UNREADABLE_MESSAGE)

            val title = entity.text("name") ?: entity.text("title") ?: "Álbum de Spotify"
            val artists = entity.text("subtitle") ?: joinArtists(entity.optJSONArray("artists"))
            val cover = findCover(entity)
            val embedTracks = parseTrackList(entity.optJSONArray("trackList"), album = title, imageUrl = cover)

            val token = findAccessToken(root)
            val apiTracks = if (token != null && embedTracks.size >= EMBED_TRUNCATION_THRESHOLD) {
                fetchAlbumTracksFromApi(albumId, token, title, cover)
            } else {
                emptyList()
            }
            val tracks = finalizeTracks(embedTracks, apiTracks)
            if (tracks.isEmpty()) throw SpotifyImportException(UNREADABLE_MESSAGE)

            RemotePlaylistPayload(
                title = title,
                platform = Platform.SPOTIFY,
                originalUrl = originalUrl,
                imageUrl = cover,
                description = artists?.let { "Álbum de $it." } ?: "Álbum importado desde Spotify.",
                tracks = tracks,
            )
        }

    suspend fun fetchTrack(trackId: String, originalUrl: String): RemotePlaylistPayload =
        withContext(Dispatchers.IO) {
            val root = fetchEmbedJson(TYPE_TRACK, trackId)
            val entity = findTrackEntity(root)
                ?: throw SpotifyImportException(UNREADABLE_MESSAGE)

            // Algunas variantes del embed devuelven la cancion dentro de un trackList de un elemento.
            val listed = entity.optJSONArray("trackList")?.optJSONObject(0)
            val title = entity.text("name") ?: entity.text("title") ?: listed?.text("title")
                ?: throw SpotifyImportException(UNREADABLE_MESSAGE)
            val artist = joinArtists(entity.optJSONArray("artists"))
                ?: entity.text("subtitle")
                ?: listed?.text("subtitle")
            val cover = findCover(entity)
            val albumName = entity.optJSONObject("album")?.text("name")
            val preview = previewUrlOf(entity) ?: listed?.let { previewUrlOf(it) }
            val duration = durationOf(entity) ?: listed?.let { durationOf(it) }

            val track = buildTrack(
                id = trackId,
                title = title,
                artist = artist,
                album = albumName,
                durationMs = duration,
                imageUrl = cover,
                previewUrl = preview,
            )
            RemotePlaylistPayload(
                title = title,
                platform = Platform.SPOTIFY,
                originalUrl = originalUrl,
                imageUrl = cover,
                description = artist?.let { "Canción de $it." } ?: "Importada desde Spotify.",
                tracks = listOf(track),
            )
        }

    /**
     * Resuelve un enlace corto (spotify.link/..., spotify.app.link/...) a la URL completa de
     * open.spotify.com. Devuelve null si no se puede determinar.
     */
    suspend fun resolveShortLink(url: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", ACCEPT_LANGUAGE)
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            val finalUrl = response.request.url.toString()
            if (OPEN_SPOTIFY_URL_REGEX.containsMatchIn(finalUrl)) {
                finalUrl
            } else {
                // Branch.io a veces responde con una pagina intermedia que contiene la URL de destino.
                val location = response.header("Location")?.let { OPEN_SPOTIFY_URL_REGEX.find(it)?.value }
                location ?: OPEN_SPOTIFY_URL_REGEX.find(response.body?.string().orEmpty())?.value
            }
        }
    }

    // ---- Embed ----

    private fun fetchEmbedJson(type: String, id: String): JSONObject {
        val request = Request.Builder()
            .url("https://open.spotify.com/embed/$type/$id")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", ACCEPT_LANGUAGE)
            .get()
            .build()
        val html = httpClient.newCall(request).execute().use { response ->
            when {
                response.code == 404 || response.code == 400 -> throw SpotifyImportException(notFoundMessage(type))
                response.code == 429 -> throw SpotifyImportException(
                    "Demasiadas peticiones a Spotify. Inténtalo de nuevo en unos minutos."
                )
                !response.isSuccessful -> throw SpotifyImportException(
                    "Spotify ha devuelto un error (${response.code}). Inténtalo de nuevo más tarde."
                )
            }
            response.body?.string().orEmpty()
        }
        return extractEmbedJson(html) ?: throw SpotifyImportException(UNREADABLE_MESSAGE)
    }

    private fun extractEmbedJson(html: String): JSONObject? {
        NEXT_DATA_REGEX.find(html)?.groupValues?.getOrNull(1)?.let { parseJsonBlock(it) }?.let { return it }
        // Plan B: cualquier bloque JSON de la pagina que contenga la lista de canciones o la entidad.
        for (match in JSON_SCRIPT_REGEX.findAll(html)) {
            val content = match.groupValues.getOrNull(1) ?: continue
            if (!content.contains("trackList") && !content.contains("entity") &&
                !content.contains(URL_ENCODED_HINT)
            ) {
                continue
            }
            parseJsonBlock(content)?.let { return it }
        }
        return null
    }

    /** Parsea un bloque JSON (en claro o URL-encoded, como usaban versiones antiguas del embed). */
    private fun parseJsonBlock(raw: String): JSONObject? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (text.startsWith("{")) {
            tryParseObject(text)?.let { return it }
        }
        val decoded = try {
            URLDecoder.decode(text, "UTF-8").trim()
        } catch (ignored: Exception) {
            null
        }
        if (decoded != null && decoded.startsWith("{")) {
            tryParseObject(decoded)?.let { return it }
        }
        return null
    }

    private fun tryParseObject(text: String): JSONObject? = try {
        JSONObject(text)
    } catch (ignored: Exception) {
        null
    }

    private fun stateOf(root: JSONObject): JSONObject? =
        root.optJSONObject("props")?.optJSONObject("pageProps")?.optJSONObject("state")

    private fun findCollectionEntity(root: JSONObject): JSONObject? {
        stateOf(root)?.optJSONObject("data")?.optJSONObject("entity")
            ?.takeIf { it.optJSONArray("trackList") != null }
            ?.let { return it }
        return deepFind(root, 0) { obj -> (obj.optJSONArray("trackList")?.length() ?: 0) > 0 }
    }

    private fun findTrackEntity(root: JSONObject): JSONObject? {
        stateOf(root)?.optJSONObject("data")?.optJSONObject("entity")
            ?.takeIf { it.text("name") != null || it.text("title") != null || it.optJSONArray("trackList") != null }
            ?.let { return it }
        return deepFind(root, 0) { obj ->
            obj.raw("uri")?.startsWith("spotify:track:") == true &&
                (obj.text("name") != null || obj.text("title") != null)
        }
    }

    private fun findAccessToken(root: JSONObject): String? {
        stateOf(root)?.optJSONObject("settings")?.optJSONObject("session")?.raw("accessToken")?.let { return it }
        return deepFind(root, 0) { obj -> obj.raw("accessToken") != null }?.raw("accessToken")
    }

    private fun deepFind(node: Any?, depth: Int, predicate: (JSONObject) -> Boolean): JSONObject? {
        if (depth > MAX_SEARCH_DEPTH) return null
        when (node) {
            is JSONObject -> {
                if (predicate(node)) return node
                val keys = node.keys()
                while (keys.hasNext()) {
                    deepFind(node.opt(keys.next()), depth + 1, predicate)?.let { return it }
                }
            }

            is JSONArray -> for (i in 0 until node.length()) {
                deepFind(node.opt(i), depth + 1, predicate)?.let { return it }
            }
        }
        return null
    }

    private fun parseTrackList(list: JSONArray?, album: String?, imageUrl: String?): List<RemoteTrackPayload> {
        if (list == null) return emptyList()
        val result = ArrayList<RemoteTrackPayload>(list.length())
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: continue
            val uri = item.raw("uri")
            // Se ignoran episodios de podcast y otros tipos que no son canciones.
            if (uri != null && !uri.startsWith("spotify:track:")) continue
            val title = item.text("title") ?: item.text("name") ?: continue
            val artist = item.text("subtitle") ?: joinArtists(item.optJSONArray("artists"))
            result += buildTrack(
                id = uri?.substringAfterLast(':'),
                title = title,
                artist = artist,
                album = album,
                durationMs = durationOf(item),
                imageUrl = findCover(item) ?: imageUrl,
                previewUrl = previewUrlOf(item),
            )
        }
        return result
    }

    private fun findCover(obj: JSONObject): String? =
        pickLargestImage(obj.optJSONObject("coverArt")?.optJSONArray("sources"))
            ?: pickLargestImage(obj.optJSONObject("visualIdentity")?.optJSONArray("image"))
            ?: pickLargestImage(obj.optJSONArray("images"))
            ?: pickLargestImage(obj.optJSONObject("album")?.optJSONArray("images"))

    private fun pickLargestImage(images: JSONArray?): String? {
        if (images == null) return null
        var best: String? = null
        var bestSize = -1
        for (i in 0 until images.length()) {
            val image = images.optJSONObject(i) ?: continue
            val url = image.raw("url") ?: continue
            val size = maxOf(image.optInt("width", 0), image.optInt("maxWidth", 0))
            if (size > bestSize) {
                best = url
                bestSize = size
            }
        }
        return best
    }

    private fun previewUrlOf(obj: JSONObject): String? =
        obj.optJSONObject("audioPreview")?.raw("url") ?: obj.raw("preview_url")

    private fun durationOf(obj: JSONObject): Long? {
        obj.optJSONObject("duration")?.optLong("totalMilliseconds", 0L)?.takeIf { it > 0 }?.let { return it }
        val direct = obj.optLong("duration", 0L).takeIf { it > 0 } ?: obj.optLong("duration_ms", 0L)
        return direct.takeIf { it > 0 }
    }

    private fun joinArtists(artists: JSONArray?): String? {
        if (artists == null) return null
        val names = (0 until artists.length()).mapNotNull { index ->
            when (val artist = artists.opt(index)) {
                is JSONObject -> artist.text("name") ?: artist.optJSONObject("profile")?.text("name")
                is String -> cleanText(artist).takeIf { it.isNotEmpty() }
                else -> null
            }
        }
        return names.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    private fun buildTrack(
        id: String?,
        title: String,
        artist: String?,
        album: String?,
        durationMs: Long?,
        imageUrl: String?,
        previewUrl: String?,
    ): RemoteTrackPayload {
        val validId = id?.takeIf { SPOTIFY_ID_REGEX.matches(it) }
        return RemoteTrackPayload(
            title = title,
            // "Spotify" es el artista comodin que PlaybackUris ignora al construir la busqueda.
            artist = artist ?: "Spotify",
            album = album,
            durationMs = durationMs,
            imageUrl = imageUrl,
            originalUrl = validId?.let { "https://open.spotify.com/track/$it" },
            previewUrl = previewUrl,
            platform = Platform.SPOTIFY,
            // Las canciones de Spotify se reproducen buscando "artista - titulo" en YouTube.
            isPlayableInApp = true,
        )
    }

    /** Combina embed + Web API (si trajo mas), deduplica conservando el orden y limita el total. */
    private fun finalizeTracks(
        embedTracks: List<RemoteTrackPayload>,
        apiTracks: List<RemoteTrackPayload>,
    ): List<RemoteTrackPayload> {
        val base = if (apiTracks.size > embedTracks.size) {
            // La API no siempre trae preview: se completa con el del embed cuando exista.
            val embedPreviews = embedTracks
                .filter { it.originalUrl != null && it.previewUrl != null }
                .associate { it.originalUrl to it.previewUrl }
            apiTracks.map { track ->
                if (track.previewUrl == null && track.originalUrl != null) {
                    track.copy(previewUrl = embedPreviews[track.originalUrl])
                } else {
                    track
                }
            }
        } else {
            embedTracks
        }
        return base
            .distinctBy { it.originalUrl ?: "${it.title.lowercase()}|${it.artist.lowercase()}" }
            .take(MAX_TRACKS)
    }

    // ---- Web API (opcional, con el token anonimo del embed) ----

    private suspend fun fetchPlaylistTracksFromApi(
        playlistId: String,
        token: String,
        coverFallback: String?,
    ): List<RemoteTrackPayload> {
        val result = ArrayList<RemoteTrackPayload>()
        var offset = 0
        var pages = 0
        while (result.size < MAX_TRACKS && pages < MAX_API_PAGES) {
            currentCoroutineContext().ensureActive()
            val url = "https://api.spotify.com/v1/playlists/$playlistId/tracks".toHttpUrl().newBuilder()
                .addQueryParameter("offset", offset.toString())
                .addQueryParameter("limit", "100")
                .addQueryParameter("fields", PLAYLIST_API_FIELDS)
                .build()
            val page = getApiJson(url, token) ?: break
            val items = page.optJSONArray("items") ?: break
            if (items.length() == 0) break
            for (i in 0 until items.length()) {
                val track = items.optJSONObject(i)?.optJSONObject("track") ?: continue
                parseApiTrack(track, albumFallback = null, coverFallback = coverFallback)?.let { result.add(it) }
            }
            offset += items.length()
            pages++
            val total = page.optInt("total", -1)
            if (page.isNull("next") || (total in 0..offset)) break
        }
        return result
    }

    private suspend fun fetchAlbumTracksFromApi(
        albumId: String,
        token: String,
        albumName: String,
        cover: String?,
    ): List<RemoteTrackPayload> {
        val result = ArrayList<RemoteTrackPayload>()
        var offset = 0
        var pages = 0
        while (result.size < MAX_TRACKS && pages < MAX_API_PAGES) {
            currentCoroutineContext().ensureActive()
            val url = "https://api.spotify.com/v1/albums/$albumId/tracks".toHttpUrl().newBuilder()
                .addQueryParameter("offset", offset.toString())
                .addQueryParameter("limit", "50")
                .build()
            val page = getApiJson(url, token) ?: break
            val items = page.optJSONArray("items") ?: break
            if (items.length() == 0) break
            for (i in 0 until items.length()) {
                val track = items.optJSONObject(i) ?: continue
                parseApiTrack(track, albumFallback = albumName, coverFallback = cover)?.let { result.add(it) }
            }
            offset += items.length()
            pages++
            val total = page.optInt("total", -1)
            if (page.isNull("next") || (total in 0..offset)) break
        }
        return result
    }

    private fun parseApiTrack(track: JSONObject, albumFallback: String?, coverFallback: String?): RemoteTrackPayload? {
        // Archivos locales del usuario y pistas eliminadas llegan sin id.
        val id = track.raw("id") ?: return null
        val title = track.text("name") ?: return null
        val album = track.optJSONObject("album")
        return buildTrack(
            id = id,
            title = title,
            artist = joinArtists(track.optJSONArray("artists")),
            album = album?.text("name") ?: albumFallback,
            durationMs = track.optLong("duration_ms", 0L).takeIf { it > 0 },
            imageUrl = pickLargestImage(album?.optJSONArray("images")) ?: coverFallback,
            previewUrl = track.raw("preview_url"),
        )
    }

    /** GET autenticado a la Web API. Cualquier fallo devuelve null: la paginacion se detiene sin romper el import. */
    private fun getApiJson(url: HttpUrl, token: String): JSONObject? = try {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .header("Accept-Language", ACCEPT_LANGUAGE)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                null
            } else {
                response.body?.string()?.let { JSONObject(it) }
            }
        }
    } catch (ignored: Exception) {
        null
    }

    // ---- Utilidades JSON ----

    /** Cadena "en bruto" (recortada) o null si falta, es null o esta vacia. */
    private fun JSONObject.raw(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return (opt(name) as? String)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Texto visible normalizado (espacios no separables a espacios normales, sin espacios repetidos). */
    private fun JSONObject.text(name: String): String? =
        raw(name)?.let { cleanText(it) }?.takeIf { it.isNotEmpty() }

    private fun cleanText(value: String): String = value.replace(WHITESPACE_REGEX, " ").trim()

    private fun notFoundMessage(type: String): String = when (type) {
        TYPE_TRACK -> "La canción no existe o no está disponible."
        TYPE_ALBUM -> "El álbum no existe o no está disponible."
        else -> "La playlist no existe o es privada."
    }

    private companion object {
        const val TYPE_PLAYLIST = "playlist"
        const val TYPE_ALBUM = "album"
        const val TYPE_TRACK = "track"

        const val UNREADABLE_MESSAGE = "No se ha podido leer la playlist de Spotify. Comprueba que es pública."

        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36"
        const val ACCEPT_LANGUAGE = "es-ES,es;q=0.9,en;q=0.8"

        /** El embed suele cortar en 100 canciones (a veces menos): a partir de aqui se intenta la API. */
        const val EMBED_TRUNCATION_THRESHOLD = 50
        const val MAX_TRACKS = 2_000
        const val MAX_API_PAGES = 40
        const val MAX_SEARCH_DEPTH = 12

        const val PLAYLIST_API_FIELDS =
            "items(track(id,name,duration_ms,artists(name),album(name,images),preview_url)),next,total"

        /** "trackList" URL-encoded no cambia, pero el JSON entero si: se busca la comilla codificada. */
        const val URL_ENCODED_HINT = "%22trackList%22"

        val NEXT_DATA_REGEX = Regex(
            """<script[^>]*\bid=["']__NEXT_DATA__["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        val JSON_SCRIPT_REGEX = Regex(
            """<script[^>]*\btype=["']application/json["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        val OPEN_SPOTIFY_URL_REGEX = Regex(
            """https?://open\.spotify\.com/(?:[a-z-]+/)?(?:playlist|album|track)/[A-Za-z0-9]{10,40}""",
        )
        val SPOTIFY_ID_REGEX = Regex("[A-Za-z0-9]{10,40}")
        val WHITESPACE_REGEX = Regex("""[   \s]+""")
    }
}
