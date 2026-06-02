package com.pausiar.openfy.domain.models

data class ParsedLink(
    val type: LinkType,
    val id: String? = null,
    val originalUrl: String,
    val normalizedUrl: String,
    val platform: Platform,
)