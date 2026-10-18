package com.pausiar.openfy.domain.models

data class Track(
    val id: Long,
    val playlistId: Long,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
    val imageUrl: String?,
    val originalUrl: String?,
    val localUri: String?,
    val previewUrl: String?,
    val platform: Platform,
    val position: Int,
    val isFavorite: Boolean,
    val isPlayableInApp: Boolean,
) {
    val playbackUri: String?
        get() = localUri
            ?: previewUrl
            ?: originalUrl?.takeIf { isPlayableInApp && platform == Platform.YOUTUBE }

    /** Reproducible dentro de Openfy: YouTube/local/preview, o Spotify mediante búsqueda en YouTube. */
    val canPlayInApp: Boolean
        get() = isPlayableInApp || !localUri.isNullOrBlank() || platform == Platform.SPOTIFY
}
