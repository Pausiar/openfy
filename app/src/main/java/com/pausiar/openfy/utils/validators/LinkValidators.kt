package com.pausiar.openfy.utils.validators

object LinkValidators {
    private val URL_REGEX = Regex("""(https?://[^\s<>"']+|spotify:[A-Za-z0-9:_-]+)""", RegexOption.IGNORE_CASE)
    private val BARE_HOST_REGEX = Regex(
        """^(?:[a-z0-9-]+\.)*(?:youtube\.com|youtu\.be|spotify\.com|spotify\.link)/\S*""",
        RegexOption.IGNORE_CASE,
    )

    fun looksLikeUrl(value: String): Boolean {
        val candidate = value.trim()
        return candidate.startsWith("http://", ignoreCase = true) ||
            candidate.startsWith("https://", ignoreCase = true) ||
            candidate.startsWith("spotify:", ignoreCase = true)
    }

    /**
     * Limpia lo que el usuario ha pegado: extrae el primer enlace aunque venga rodeado de texto
     * ("Mira esta playlist https://..."), completa enlaces sin esquema (youtube.com/...) y quita
     * barras o signos finales.
     */
    fun sanitizeUrl(value: String): String {
        val trimmed = value.trim()
        val found = URL_REGEX.find(trimmed)?.value
            ?: BARE_HOST_REGEX.find(trimmed)?.value?.let { "https://$it" }
            ?: trimmed
        return found.trimEnd('/', ',', ';', ')', '.')
    }
}
