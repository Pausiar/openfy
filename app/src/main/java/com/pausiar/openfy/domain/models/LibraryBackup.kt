package com.pausiar.openfy.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class LibraryBackup(
    val exportedAt: Long,
    val playlists: List<PlaylistBackup>,
    val tracks: List<TrackBackup>,
    val history: List<PlaybackHistoryBackup>,
    val settings: BackupSettings? = null,
)

@Serializable
data class PlaylistBackup(
    val id: Long,
    val title: String,
    val platform: String,
    val originalUrl: String,
    val imageUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val isLocal: Boolean,
    val description: String?,
)

@Serializable
data class TrackBackup(
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
    val platform: String,
    val position: Int,
    val isFavorite: Boolean,
    val isPlayableInApp: Boolean,
)

@Serializable
data class PlaybackHistoryBackup(
    val id: Long,
    val trackId: Long,
    val playedAt: Long,
    val progressMs: Long,
)

@Serializable
data class BackupSettings(
    val themeMode: String,
    val autoplayEnabled: Boolean,
    val repeatPreference: String,
    val shuffleEnabled: Boolean,
    val lastPlaylistId: Long? = null,
)