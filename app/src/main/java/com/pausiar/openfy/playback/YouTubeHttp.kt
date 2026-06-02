package com.pausiar.openfy.playback

object YouTubeHttp {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

    val defaultHeaders: Map<String, String> = mapOf(
        "Referer" to "https://www.youtube.com",
        "Origin" to "https://www.youtube.com",
    )

    fun extractVideoId(url: String?): String? {
        val value = url?.trim().orEmpty()
        if (value.isBlank()) return null

        Regex("""[?&]v=([a-zA-Z0-9_-]{11})""").find(value)?.groupValues?.get(1)?.let { return it }
        Regex("""youtu\.be/([a-zA-Z0-9_-]{11})""").find(value)?.groupValues?.get(1)?.let { return it }
        Regex("""/shorts/([a-zA-Z0-9_-]{11})""").find(value)?.groupValues?.get(1)?.let { return it }
        return null
    }
}
