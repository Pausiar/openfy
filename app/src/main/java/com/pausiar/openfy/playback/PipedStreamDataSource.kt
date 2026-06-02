package com.pausiar.openfy.playback

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class PipedStreamDataSource(
    private val httpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun resolveStreamUrl(videoId: String): String? {
        val endpoints = listOf(
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.adminforge.de",
            "https://api.piped.yt",
        )

        for (baseUrl in endpoints) {
            val url = resolveFromEndpoint(baseUrl, videoId) ?: continue
            return url
        }
        return null
    }

    private fun resolveFromEndpoint(baseUrl: String, videoId: String): String? {
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
                    ?.maxByOrNull { it.bitrate ?: 0 }
                    ?.url
                    ?.takeIf { it.isNotBlank() }
                    ?: payload.videoStreams
                        ?.filterNot { it.videoOnly == true }
                        ?.maxByOrNull { it.bitrate ?: 0 }
                        ?.url
                        ?.takeIf { it.isNotBlank() }
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
    )
}
