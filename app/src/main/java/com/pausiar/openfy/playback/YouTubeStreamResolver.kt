package com.pausiar.openfy.playback

import com.pausiar.openfy.domain.models.Platform
import com.pausiar.openfy.domain.models.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

class YouTubeStreamResolver(
    private val httpClient: OkHttpClient,
    private val pipedStreamDataSource: PipedStreamDataSource,
) {
    private var cachedInnertubeApiKey: String? = null

    suspend fun resolvePlaybackUri(track: Track): String? = withContext(Dispatchers.IO) {
        when {
            !track.localUri.isNullOrBlank() -> track.localUri
            !track.previewUrl.isNullOrBlank() -> track.previewUrl
            track.platform == Platform.YOUTUBE && track.isPlayableInApp -> {
                val videoId = YouTubeHttp.extractVideoId(track.originalUrl) ?: return@withContext null
                resolveYouTubeVideo(videoId, track.originalUrl)
            }
            else -> null
        }
    }

    private fun resolveYouTubeVideo(videoId: String, watchUrl: String?): String? =
        resolveWithNewPipe(watchUrl)
            ?: pipedStreamDataSource.resolveStreamUrl(videoId)
            ?: resolveWithInnertubePlayer(videoId)

    private fun resolveWithNewPipe(watchUrl: String?): String? {
        val url = watchUrl?.trim().orEmpty()
        if (url.isBlank()) return null

        return runCatching {
            val service = NewPipe.getService(ServiceList.YouTube.serviceId)
            val streamInfo = StreamInfo.getInfo(service, url)

            streamInfo.audioStreams
                .maxByOrNull { it.averageBitrate }
                ?.content
                ?: streamInfo.videoStreams
                    .filterNot { it.isVideoOnly }
                    .maxByOrNull { it.bitrate }
                    ?.content
                ?: streamInfo.videoStreams
                    .maxByOrNull { it.bitrate }
                    ?.content
                ?: streamInfo.hlsUrl?.takeIf { it.isNotBlank() }
                ?: streamInfo.dashMpdUrl?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

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

        val request = Request.Builder()
            .url("https://www.youtube.com")
            .header("User-Agent", YouTubeHttp.USER_AGENT)
            .build()

        val key = runCatching {
            val response = httpClient.newCall(request).execute()
            response.use { httpResponse ->
                if (!httpResponse.isSuccessful) return@runCatching ""
                val html = httpResponse.body?.string().orEmpty()
                Regex(""""INNERTUBE_API_KEY"\s*:\s*"([^"]+)"""")
                    .find(html)
                    ?.groupValues
                    ?.get(1)
                    .orEmpty()
            }
        }.getOrDefault("")

        cachedInnertubeApiKey = key
        return key
    }
}
