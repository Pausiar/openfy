package com.pausiar.openfy.data.remote.spotify

import kotlinx.serialization.Serializable

@Serializable
data class SpotifyPlaylistDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val originalUrl: String? = null,
    val tracks: List<SpotifyTrackDto> = emptyList(),
)

@Serializable
data class SpotifyTrackDto(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
    val imageUrl: String? = null,
    val originalUrl: String? = null,
    val previewUrl: String? = null,
)