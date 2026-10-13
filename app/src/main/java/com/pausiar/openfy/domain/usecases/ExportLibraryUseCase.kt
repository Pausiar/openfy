package com.pausiar.openfy.domain.usecases

import com.pausiar.openfy.data.local.entity.PlaybackHistoryEntity
import com.pausiar.openfy.data.local.entity.PlaylistEntity
import com.pausiar.openfy.data.local.entity.TrackEntity
import com.pausiar.openfy.data.repository.MusicRepository
import com.pausiar.openfy.data.repository.SettingsRepository
import com.pausiar.openfy.domain.models.BackupSettings
import com.pausiar.openfy.domain.models.LibraryBackup
import com.pausiar.openfy.domain.models.PlaybackHistoryBackup
import com.pausiar.openfy.domain.models.PlaylistBackup
import com.pausiar.openfy.domain.models.SettingsState
import com.pausiar.openfy.domain.models.TrackBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ExportLibraryUseCase(
    private val musicRepository: MusicRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val json = Json { prettyPrint = true }

    suspend operator fun invoke(): String {
        val settings = settingsRepository.settings.first()
        val playlists = musicRepository.getAllPlaylists()
        val tracks = musicRepository.getAllTracks()
        val history = musicRepository.getAllHistory()
        // Mapeo y serializacion de bibliotecas grandes fuera del hilo principal.
        return withContext(Dispatchers.Default) { buildJson(settings, playlists, tracks, history) }
    }

    private fun buildJson(
        settings: SettingsState,
        playlists: List<PlaylistEntity>,
        tracks: List<TrackEntity>,
        history: List<PlaybackHistoryEntity>,
    ): String {
        val backup = LibraryBackup(
            exportedAt = System.currentTimeMillis(),
            playlists = playlists.map { playlist ->
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
            tracks = tracks.map { track ->
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
            history = history.map { entry ->
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