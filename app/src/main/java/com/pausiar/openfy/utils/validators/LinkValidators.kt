package com.pausiar.openfy.utils.validators

object LinkValidators {
    fun looksLikeUrl(value: String): Boolean {
        val candidate = value.trim()
        return candidate.startsWith("http://") ||
            candidate.startsWith("https://") ||
            candidate.startsWith("spotify:")
    }

    fun sanitizeUrl(value: String): String = value.trim().removeSuffix("/")
}