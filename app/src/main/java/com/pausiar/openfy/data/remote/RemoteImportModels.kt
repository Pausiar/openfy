package com.pausiar.openfy.data.remote

import com.pausiar.openfy.domain.models.Platform

data class RemotePlaylistPayload(
    val title: String,
    val platform: Platform,
    val originalUrl: String,
    val imageUrl: String?,
    val description: String?,
    val tracks: List<RemoteTrackPayload>,
    val requiresApiConfiguration: Boolean = false,
)

data class RemoteTrackPayload(
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
    val imageUrl: String?,
    val originalUrl: String?,
    val localUri: String? = null,
    val previewUrl: String? = null,
    val platform: Platform,
    val isFavorite: Boolean = false,
    val isPlayableInApp: Boolean,
)