package com.pausiar.openfy.domain.models

enum class Platform(val displayName: String) {
    SPOTIFY("Spotify"),
    YOUTUBE("YouTube"),
    LOCAL("Local"),
    DEMO("Demo"),
    UNKNOWN("Desconocido");

    companion object {
        fun fromValue(raw: String?): Platform = entries.firstOrNull { it.name == raw } ?: UNKNOWN
    }
}