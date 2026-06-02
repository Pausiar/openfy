package com.pausiar.openfy.domain.models

data class Playlist(
    val id: Long,
    val title: String,
    val platform: Platform,
    val originalUrl: String,
    val imageUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val isLocal: Boolean,
    val description: String?,
    val tracks: List<Track>,
)

data class PlaylistSummary(
    val id: Long,
    val title: String,
    val platform: Platform,
    val originalUrl: String,
    val imageUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val isLocal: Boolean,
    val description: String?,
    val trackCount: Int,
    val playableTrackCount: Int,
)