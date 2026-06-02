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
    suspend fun fetchPlaylist(playlistId: String, originalUrl: String): RemotePlaylistPayload? =
        fetchFromInnertube(playlistId, originalUrl) ?: fetchFromPlaylistPage(playlistId, originalUrl)

    suspend fun fetchVideo(videoId: String, originalUrl: String): RemotePlaylistPayload? = withContext(Dispatchers.IO) {
        val title = fetchVideoTitle(videoId) ?: "Video de YouTube"
        RemotePlaylistPayload(
            title = title,
            platform = Platform.YOUTUBE,
            originalUrl = originalUrl,
            imageUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            description = "Importado desde YouTube.",
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
                .url("$INNERTUBE_BROWSE_URL?key=$INNERTUBE_API_KEY")
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

    private fun parseInnertubePlaylist(json: String, originalUrl: String): RemotePlaylistPayload? {
        val title = Regex(""""text"\s*:\s*"([^"]+)"""")
            .findAll(json)
            .map { it.groupValues[1] }
            .firstOrNull { it.length in 3..120 && !it.startsWith("http") }
            ?: "Playlist de YouTube"

        val entries = extractVideoEntries(json)
        if (entries.isEmpty()) return null

        return RemotePlaylistPayload(
            title = title,
            platform = Platform.YOUTUBE,
            originalUrl = originalUrl,
            imageUrl = entries.firstOrNull()?.imageUrl,
            description = "Importada desde YouTube.",
            tracks = entries.mapIndexed { index, entry ->
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
    }

    private fun parsePlaylistHtml(html: String, originalUrl: String): RemotePlaylistPayload? {
        val title = Regex("""<meta name="title" content="([^"]+)"""")
            .find(html)
            ?.groupValues
            ?.get(1)
            ?.substringBefore(" - YouTube")
            ?.trim()
            ?: "Playlist de YouTube"

        val entries = extractVideoEntries(html)
        if (entries.isEmpty()) return null

        return RemotePlaylistPayload(
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
    }

    private fun extractVideoEntries(source: String): List<VideoEntry> {
        val videoIds = LinkedHashSet<String>()
        VIDEO_ID_REGEX.findAll(source).forEach { match ->
            videoIds.add(match.groupValues[1])
        }

        return videoIds.mapNotNull { videoId ->
            val title = findNearbyTitle(source, videoId) ?: "Video $videoId"
            VideoEntry(
                videoId = videoId,
                title = title,
                channel = "YouTube",
                durationMs = null,
                imageUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            )
        }
    }

    private fun findNearbyTitle(source: String, videoId: String): String? {
        val index = source.indexOf(videoId)
        if (index < 0) return null
        val windowStart = (index - 500).coerceAtLeast(0)
        val windowEnd = (index + 500).coerceAtMost(source.length)
        val window = source.substring(windowStart, windowEnd)
        return TITLE_REGEX.findAll(window)
            .map { it.groupValues[1] }
            .firstOrNull { title ->
                title.length in 2..120 &&
                    !title.contains("http") &&
                    !title.equals("YouTube", ignoreCase = true)
            }
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

    private data class VideoEntry(
        val videoId: String,
        val title: String,
        val channel: String?,
        val durationMs: Long?,
        val imageUrl: String?,
    )

    private companion object {
        const val INNERTUBE_API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
        const val INNERTUBE_BROWSE_URL = "https://www.youtube.com/youtubei/v1/browse"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val VIDEO_ID_REGEX = Regex("""videoId["\\]*\s*:\s*["\\]*([a-zA-Z0-9_-]{11})""")
        val TITLE_REGEX = Regex("""title["\\]*\s*:\s*["\\]*([^"\\]{2,120})""")
    }
}
