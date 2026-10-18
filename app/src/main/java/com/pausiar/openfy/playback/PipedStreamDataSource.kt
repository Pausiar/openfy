package com.pausiar.openfy.playback

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class PipedStreamDataSource(
    httpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // Las instancias de Piped suelen estar caidas o lentas: timeouts cortos para no bloquear.
    private val httpClient: OkHttpClient = httpClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    val endpoints: List<String> = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de",
        "https://api.piped.yt",
    )

    fun resolveStreamUrl(videoId: String): String? {
        for (baseUrl in endpoints) {
            val url = resolveFromEndpoint(baseUrl, videoId) ?: continue
            return url
        }
        return null
    }

    fun resolveFromEndpoint(baseUrl: String, videoId: String): String? {
        val request = Request.Builder()
            .url("$baseUrl/streams/$videoId")
            .header("User-Agent", YouTubeHttp.USER_AGENT)
            .build()

        val response = runCatching { httpClient.newCall(request).execute() }.getOrNull() ?: return null
        return response.use { httpResponse ->
            if (!httpResponse.isSuccessful) return null
            val body = httpResponse.body?.string().orEmpty()
            if (body.isBlank()) return null

            runCatching {
                val payload = json.decodeFromString<PipedStreamResponse>(body)
                payload.audioStreams
                    ?.filter { !it.url.isNullOrBlank() }
                    ?.maxWithOrNull(
                        compareBy<PipedStream>(
                            { it.audioTrackType == null || it.audioTrackType.equals("ORIGINAL", ignoreCase = true) },
                            { it.bitrate ?: 0 },
                        )
                    )
                    ?.url
                    ?: payload.videoStreams
                        ?.filter { it.videoOnly != true && !it.url.isNullOrBlank() }
                        ?.maxByOrNull { it.bitrate ?: 0 }
                        ?.url
            }.getOrNull()
        }
    }

    @Serializable
    private data class PipedStreamResponse(
        val audioStreams: List<PipedStream>? = null,
        val videoStreams: List<PipedStream>? = null,
    )

    @Serializable
    private data class PipedStream(
        val url: String? = null,
        val bitrate: Int? = null,
        @SerialName("videoOnly") val videoOnly: Boolean? = null,
        @SerialName("audioTrackType") val audioTrackType: String? = null,
    )
}
