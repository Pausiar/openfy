package com.pausiar.openfy.domain.usecases

import com.pausiar.openfy.data.local.entity.PlaybackHistoryEntity
import com.pausiar.openfy.data.local.entity.PlaylistEntity
import com.pausiar.openfy.data.local.entity.TrackEntity
import com.pausiar.openfy.data.repository.MusicRepository
import com.pausiar.openfy.data.repository.SettingsRepository
import com.pausiar.openfy.domain.models.LibraryBackup
import com.pausiar.openfy.domain.models.RepeatPreference
import com.pausiar.openfy.domain.models.SettingsState
import com.pausiar.openfy.domain.models.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class ImportLibraryDumpUseCase(
    private val musicRepository: MusicRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend operator fun invoke(raw: String): Result<Unit> {
        // Decodificar y mapear un backup grande no debe bloquear el hilo principal.
        val backup = withContext(Dispatchers.Default) {
            try {
                json.decodeFromString<LibraryBackup>(raw)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IllegalArgumentException) { // incluye SerializationException
                null
            }
        } ?: return Result.failure(IllegalArgumentException("El archivo no es una copia de seguridad válida de Openfy."))

        return try {
            withContext(Dispatchers.Default) { restore(backup) }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(
                IllegalStateException("No se ha podido restaurar la copia de seguridad. Tus datos actuales no se han modificado.", error)
            )
        }
    }

    private suspend fun restore(backup: LibraryBackup) {
        musicRepository.replaceLibrary(
            playlists = backup.playlists.map { playlist ->
                PlaylistEntity(
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
            tracks = backup.tracks.map { track ->
                TrackEntity(
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
            history = backup.history.map { entry ->
                PlaybackHistoryEntity(
                    id = entry.id,
                    trackId = entry.trackId,
                    playedAt = entry.playedAt,
                    progressMs = entry.progressMs,
                )
            },
        )
        backup.settings?.let { settings ->
            settingsRepository.overwrite(
                SettingsState(
                    themeMode = ThemeMode.entries.firstOrNull { it.name == settings.themeMode } ?: ThemeMode.DARK,
                    autoplayEnabled = settings.autoplayEnabled,
                    repeatPreference = RepeatPreference.entries.firstOrNull {
                        it.name == settings.repeatPreference
                    } ?: RepeatPreference.OFF,
                    shuffleEnabled = settings.shuffleEnabled,
                    lastPlaylistId = settings.lastPlaylistId?.takeIf { id -> backup.playlists.any { it.id == id } },
                )
            )
        }
    }
}