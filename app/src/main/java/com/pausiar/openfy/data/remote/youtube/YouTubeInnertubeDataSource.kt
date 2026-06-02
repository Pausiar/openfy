package com.pausiar.openfy.data.remote.youtube

import com.pausiar.openfy.data.remote.RemotePlaylistPayload
import com.pausiar.openfy.data.remote.RemoteTrackPayload
import com.pausiar.openfy.domain.models.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class YouTubeInnertubeDataSource(
    private val httpClient: OkHttpClient,
) {
    private var cachedInnertubeApiKey: String? = null

    suspend fun fetchPlaylist(playlistId: String, originalUrl: String): RemotePlaylistPayload? =
        fetchFromInnertube(playlistId, originalUrl) ?: fetchFromPlaylistPage(playlistId, originalUrl)

    suspend fun fetchVideo(videoId: String, originalUrl: String): RemotePlaylistPayload? = withContext(Dispatchers.IO) {
        val title = fetchVideoTitle(videoId) ?: "Video de YouTube"
        RemotePlaylistPayload(
            title = title,
            platform = Platform.YOUTUBE,
            originalUrl = originalUrl,
            imageUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            description = "Importada desde YouTube.",
            tracks = listOf(
                RemoteTrackPayload(
                    title = title,
                    artist = "YouTube",
                    album = null,
                    durationMs = null,
                    imageUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                    originalUrl = "https://www.youtube.com/watch?v=$videoId",
                    platform = Platform.YOUTUBE,
                    isPlayableInApp = true,
                )
            ),
        )
    }

    private suspend fun fetchFromInnertube(playlistId: String, originalUrl: String): RemotePlaylistPayload? =
        withContext(Dispatchers.IO) {
            val apiKey = resolveInnertubeApiKey()
            if (apiKey.isBlank()) return@withContext null

            val body = JSONObject()
                .put(
                    "context",
                    JSONObject().put(
                        "client",
                        JSONObject()
                            .put("clientName", "WEB")
                            .put("clientVersion", "2.20240401.00.00")
                            .put("hl", "es")
                            .put("gl", "ES"),
                    ),
                )
                .put("browseId", "VL$playlistId")
                .toString()

            val request = Request.Builder()
                .url("$INNERTUBE_BROWSE_URL?key=$apiKey")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json")
                .build()

            val response = runCatching { httpClient.newCall(request).execute() }.getOrNull() ?: return@withContext null
            response.use { httpResponse ->
                if (!httpResponse.isSuccessful) return@withContext null
                val json = httpResponse.body?.string().orEmpty()
                if (json.isBlank()) return@withContext null
                parseInnertubePlaylist(json, originalUrl)
            }
        }

    private suspend fun fetchFromPlaylistPage(playlistId: String, originalUrl: String): RemotePlaylistPayload? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://www.youtube.com/playlist?list=$playlistId")
                .header("User-Agent", USER_AGENT)
                .build()

            val response = runCatching { httpClient.newCall(request).execute() }.getOrNull() ?: return@withContext null
            response.use { httpResponse ->
                if (!httpResponse.isSuccessful) return@withContext null
                val html = httpResponse.body?.string().orEmpty()
                if (html.isBlank()) return@withContext null
                parsePlaylistHtml(html, originalUrl)
            }
        }

    private suspend fun parseInnertubePlaylist(json: String, originalUrl: String): RemotePlaylistPayload? {
        val title = Regex(""""text"\s*:\s*"([^"]+)"""")
            .findAll(json)
            .map { it.groupValues[1] }
            .firstOrNull { it.length in 3..120 && !it.startsWith("http") }
            ?: "Playlist de YouTube"

        val entries = enrichEntries(extractVideoEntries(json))
        if (entries.isEmpty()) return null

        return buildPayload(title, originalUrl, entries)
    }

    private suspend fun parsePlaylistHtml(html: String, originalUrl: String): RemotePlaylistPayload? {
        val title = Regex("""<meta name="title" content="([^"]+)"""")
            .find(html)
            ?.groupValues
            ?.get(1)
            ?.substringBefore(" - YouTube")
            ?.trim()
            ?: "Playlist de YouTube"

        val entries = enrichEntries(extractVideoEntries(html))
        if (entries.isEmpty()) return null

        return buildPayload(title, originalUrl, entries)
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

    private suspend fun enrichEntries(entries: List<VideoEntry>): List<VideoEntry> = withContext(Dispatchers.IO) {
        entries.map { entry ->
            if (entry.title.startsWith("Video ") && entry.videoId.length == 11) {
                val resolvedTitle = fetchVideoTitle(entry.videoId)
                if (resolvedTitle != null) entry.copy(title = resolvedTitle) else entry
            } else {
                entry
            }
        }
    }

    private fun extractVideoEntries(source: String): List<VideoEntry> {
        val fromRenderers = VIDEO_RENDERER_REGEX.findAll(source)
            .mapNotNull { match -> parseVideoRendererBlock(match.value) }
            .distinctBy { it.videoId }
            .toList()

        if (fromRenderers.isNotEmpty()) return fromRenderers

        val videoIds = LinkedHashSet<String>()
        VIDEO_ID_REGEX.findAll(source).forEach { match ->
            videoIds.add(match.groupValues[1])
        }

        return videoIds.map { videoId ->
            VideoEntry(
                videoId = videoId,
                title = findNearbyTitle(source, videoId) ?: "Video $videoId",
                channel = findNearbyChannel(source, videoId) ?: "YouTube",
                durationMs = findNearbyDuration(source, videoId),
                imageUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            )
        }
    }

    private fun parseVideoRendererBlock(block: String): VideoEntry? {
        val videoId = Regex(""""videoId"\s*:\s*"([a-zA-Z0-9_-]{11})"""")
            .find(block)
            ?.groupValues
            ?.get(1)
            ?: return null

        val title = extractRunsText(block, "title")
            ?: findNearbyTitle(block, videoId)
            ?: return null

        return VideoEntry(
            videoId = videoId,
            title = title,
            channel = extractRunsText(block, "shortBylineText")
                ?: extractRunsText(block, "longBylineText")
                ?: extractRunsText(block, "ownerText")
                ?: "YouTube",
            durationMs = extractSimpleText(block, "lengthText")?.let(::parseDurationLabel),
            imageUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
        )
    }

    private fun extractRunsText(block: String, field: String): String? {
        val pattern = Regex(""""$field"\s*:\s*\{\s*"runs"\s*:\s*\[\s*\{\s*"text"\s*:\s*"([^"]+)"""")
        return pattern.find(block)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun extractSimpleText(block: String, field: String): String? {
        val pattern = Regex(""""$field"\s*:\s*\{\s*"simpleText"\s*:\s*"([^"]+)"""")
        return pattern.find(block)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun findNearbyTitle(source: String, videoId: String): String? {
        val index = source.indexOf(videoId)
        if (index < 0) return null
        val window = source.substring(
            (index - 700).coerceAtLeast(0),
            (index + 700).coerceAtMost(source.length),
        )
        return RUNS_TITLE_REGEX.find(window)?.groupValues?.get(1)
            ?: TITLE_REGEX.findAll(window)
                .map { it.groupValues[1] }
                .firstOrNull { title ->
                    title.length in 2..120 &&
                        !title.contains("http") &&
                        !title.equals("YouTube", ignoreCase = true)
                }
    }

    private fun findNearbyChannel(source: String, videoId: String): String? {
        val index = source.indexOf(videoId)
        if (index < 0) return null
        val window = source.substring(
            (index - 700).coerceAtLeast(0),
            (index + 700).coerceAtMost(source.length),
        )
        return CHANNEL_REGEX.find(window)?.groupValues?.get(1)
    }

    private fun findNearbyDuration(source: String, videoId: String): Long? {
        val index = source.indexOf(videoId)
        if (index < 0) return null
        val window = source.substring(
            (index - 700).coerceAtLeast(0),
            (index + 700).coerceAtMost(source.length),
        )
        return LENGTH_REGEX.find(window)?.groupValues?.get(1)?.let(::parseDurationLabel)
    }

    private fun parseDurationLabel(raw: String): Long? {
        val parts = raw.trim().split(":").mapNotNull { it.trim().toLongOrNull() }
        val totalSeconds = when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> return null
        }
        return totalSeconds * 1000
    }

    private suspend fun fetchVideoTitle(videoId: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json")
            .header("User-Agent", USER_AGENT)
            .build()
        val response = runCatching { httpClient.newCall(request).execute() }.getOrNull() ?: return@withContext null
        response.use { httpResponse ->
            if (!httpResponse.isSuccessful) return@withContext null
            val json = httpResponse.body?.string().orEmpty()
            runCatching { JSONObject(json).optString("title") }.getOrNull()?.takeIf { it.isNotBlank() }
        }
    }

    private suspend fun resolveInnertubeApiKey(): String {
        cachedInnertubeApiKey?.let { if (it.isNotBlank()) return it }

        val request = Request.Builder()
            .url("https://www.youtube.com")
            .header("User-Agent", USER_AGENT)
            .build()

        val key = withContext(Dispatchers.IO) {
            val response = runCatching { httpClient.newCall(request).execute() }.getOrNull() ?: return@withContext ""
            response.use { httpResponse ->
                if (!httpResponse.isSuccessful) return@withContext ""
                val html = httpResponse.body?.string().orEmpty()
                INNERTUBE_KEY_REGEX.find(html)?.groupValues?.get(1).orEmpty()
            }
        }

        cachedInnertubeApiKey = key
        return key
    }

    private data class VideoEntry(
        val videoId: String,
        val title: String,
        val channel: String?,
        val durationMs: Long?,
        val imageUrl: String?,
    )

    private companion object {
        const val INNERTUBE_BROWSE_URL = "https://www.youtube.com/youtubei/v1/browse"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val VIDEO_RENDERER_REGEX = Regex(""""videoRenderer"\s*:\s*\{""")
        val VIDEO_ID_REGEX = Regex(""""videoId"\s*:\s*"([a-zA-Z0-9_-]{11})"""")
        val RUNS_TITLE_REGEX = Regex(""""runs"\s*:\s*\[\s*\{\s*"text"\s*:\s*"([^"]+)"""")
        val TITLE_REGEX = Regex(""""title"\s*:\s*"([^"]+)"""")
        val CHANNEL_REGEX = Regex(""""shortBylineText"\s*:\s*\{\s*"runs"\s*:\s*\[\s*\{\s*"text"\s*:\s*"([^"]+)"""")
        val LENGTH_REGEX = Regex(""""lengthText"\s*:\s*\{\s*"simpleText"\s*:\s*"([^"]+)"""")
        val INNERTUBE_KEY_REGEX = Regex(""""INNERTUBE_API_KEY"\s*:\s*"([^"]+)"""")
    }
}
