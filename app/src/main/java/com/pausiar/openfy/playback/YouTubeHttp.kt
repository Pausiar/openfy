package com.pausiar.openfy.playback

object YouTubeHttp {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

    /** UA del cliente iOS de YouTube: las URLs de googlevideo con `c=IOS` lo esperan. */
    const val IOS_USER_AGENT =
        "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X; US)"

    /** UA del cliente Android de YouTube: las URLs de googlevideo con `c=ANDROID` lo esperan. */
    const val ANDROID_USER_AGENT =
        "com.google.android.youtube/19.28.35 (Linux; U; Android 14; US) gzip"

    /** Tiempo de vida asumido de una URL de stream cuando no trae parametro `expire`. */
    const val DEFAULT_STREAM_TTL_MS: Long = 5L * 60L * 60L * 1000L

    /** Margen de seguridad antes de la expiracion real de la URL. */
    private const val EXPIRY_SAFETY_MARGIN_MS: Long = 10L * 60L * 1000L

    private const val HEADER_USER_AGENT = "User-Agent"

    val defaultHeaders: Map<String, String> = mapOf(
        "Referer" to "https://www.youtube.com",
        "Origin" to "https://www.youtube.com",
    )

    private val videoIdRegex = Regex("""^[a-zA-Z0-9_-]{11}$""")
    private val clientParamRegex = Regex("""[?&]c=([A-Za-z0-9_]+)""")
    private val expireParamRegex = Regex("""[?&/]expire[=/](\d+)""")

    fun isValidVideoId(value: String?): Boolean = value != null && videoIdRegex.matches(value)

    fun watchUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"

    fun extractVideoId(url: String?): String? {
        val value = url?.trim().orEmpty()
        if (value.isBlank()) return null

        Regex("""[?&]v=([a-zA-Z0-9_-]{11})""").find(value)?.groupValues?.get(1)?.let { return it }
        Regex("""youtu\.be/([a-zA-Z0-9_-]{11})""").find(value)?.groupValues?.get(1)?.let { return it }
        Regex("""/shorts/([a-zA-Z0-9_-]{11})""").find(value)?.groupValues?.get(1)?.let { return it }
        Regex("""/(?:embed|live)/([a-zA-Z0-9_-]{11})""").find(value)?.groupValues?.get(1)?.let { return it }
        return null
    }

    /**
     * Cabeceras HTTP a usar al descargar [streamUrl]. Las URLs generadas para los clientes
     * nativos de YouTube (iOS / Android) se piden con el UA de ese cliente, como hace NewPipe;
     * el resto mantiene el comportamiento anterior (UA de navegador + Referer/Origin).
     */
    fun playbackHeadersFor(streamUrl: String): Map<String, String> {
        val client = clientParamRegex.find(streamUrl)?.groupValues?.get(1)?.uppercase()
        return when (client) {
            "IOS" -> mapOf(HEADER_USER_AGENT to IOS_USER_AGENT)
            "ANDROID" -> mapOf(HEADER_USER_AGENT to ANDROID_USER_AGENT)
            else -> defaultHeaders + (HEADER_USER_AGENT to USER_AGENT)
        }
    }

    /** Instante (epoch ms) a partir del cual la URL debe considerarse caducada. */
    fun expiryFor(streamUrl: String, nowMs: Long = System.currentTimeMillis()): Long {
        val expireSeconds = expireParamRegex.find(streamUrl)?.groupValues?.get(1)?.toLongOrNull()
        val defaultExpiry = nowMs + DEFAULT_STREAM_TTL_MS
        if (expireSeconds == null || expireSeconds <= 0L) return defaultExpiry
        val expiry = expireSeconds * 1000L - EXPIRY_SAFETY_MARGIN_MS
        // Si la URL ya esta casi caducada la damos por valida solo un minuto.
        return expiry.coerceIn(nowMs + 60_000L, defaultExpiry)
    }
}
