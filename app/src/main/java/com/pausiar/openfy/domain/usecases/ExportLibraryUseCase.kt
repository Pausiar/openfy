package com.pausiar.openfy.domain.usecases

import com.pausiar.openfy.data.repository.MusicRepository
import com.pausiar.openfy.data.repository.SettingsRepository
import com.pausiar.openfy.domain.models.BackupSettings
import com.pausiar.openfy.domain.models.LibraryBackup
import com.pausiar.openfy.domain.models.PlaybackHistoryBackup
import com.pausiar.openfy.domain.models.PlaylistBackup
import com.pausiar.openfy.domain.models.TrackBackup
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ExportLibraryUseCase(
    private val musicRepository: MusicRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val json = Json { prettyPrint = true }

    suspend operator fun invoke(): String {
        val settings = settingsRepository.settings.first()
        val backup = LibraryBackup(
            exportedAt = System.currentTimeMillis(),
            playlists = musicRepository.getAllPlaylists().map { playlist ->
                PlaylistBackup(
                    id = playlist.id,
                    title = playlist.title,
                    platform = playlist.platform,
                    originalUrl = playlist.originalUrl,
                    imageUrl = playlist.imageUrl,
                    createdAt = playlist.createdAt,
                    updatedAt = playlist.updatedAt,
                    isLocal = playlist.isLocal,
                    description = playlist.description,
                )
            },
            tracks = musicRepository.getAllTracks().map { track ->
                TrackBackup(
                    id = track.id,
                    playlistId = track.playlistId,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = track.durationMs,
                    imageUrl = track.imageUrl,
                    originalUrl = track.originalUrl,
                    localUri = track.localUri,
                    previewUrl = track.previewUrl,
                    platform = track.platform,
                    position = track.position,
                    isFavorite = track.isFavorite,
                    isPlayableInApp = track.isPlayableInApp,
                )
            },
            history = musicRepository.getAllHistory().map { entry ->
                PlaybackHistoryBackup(
                    id = entry.id,
                    trackId = entry.trackId,
                    playedAt = entry.playedAt,
                    progressMs = entry.progressMs,
                )
            },
            settings = BackupSettings(
                themeMode = settings.themeMode.name,
                autoplayEnabled = settings.autoplayEnabled,
                repeatPreference = settings.repeatPreference.name,
                shuffleEnabled = settings.shuffleEnabled,
                lastPlaylistId = settings.lastPlaylistId,
            ),
        )
        return json.encodeToString(backup)
    }
}