package com.pausiar.openfy.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pausiar.openfy.data.repository.MusicRepository
import com.pausiar.openfy.data.repository.SettingsRepository
import com.pausiar.openfy.domain.models.Playlist
import com.pausiar.openfy.domain.models.Track
import com.pausiar.openfy.domain.models.TrackFilter
import com.pausiar.openfy.domain.models.TrackSort
import com.pausiar.openfy.domain.usecases.ObservePlaylistDetailUseCase
import com.pausiar.openfy.player.controller.PlayerConnection
import com.pausiar.openfy.utils.normalizeForSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    val filter: TrackFilter = TrackFilter(),
    val tracks: List<Track> = emptyList(),
    val message: String? = null,
)

class PlaylistDetailViewModel(
    private val playlistId: Long,
    observePlaylistDetailUseCase: ObservePlaylistDetailUseCase,
    private val musicRepository: MusicRepository,
    private val playerConnection: PlayerConnection,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val filter = MutableStateFlow(TrackFilter())
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PlaylistDetailUiState> = combine(
        observePlaylistDetailUseCase(playlistId),
        filter,
        message,
    ) { playlist, activeFilter, info ->
        PlaylistDetailUiState(
            playlist = playlist,
            filter = activeFilter,
            tracks = playlist?.tracks.orEmpty().applyFilter(activeFilter),
            message = info,
        )
    }
        .flowOn(Dispatchers.Default) // filtrado y orden de playlists grandes fuera del hilo principal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistDetailUiState())

    init {
        viewModelScope.launch {
            try {
                settingsRepository.setLastPlaylistId(playlistId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (ignored: Exception) {
                // Recordar la ultima playlist es opcional.
            }
        }
        viewModelScope.launch {
            playerConnection.uiState.collect { playerState ->
                playerState.errorMessage?.let { info ->
                    message.value = info
                    playerConnection.clearError()
                }
            }
        }
    }

    fun updateQuery(value: String) {
        filter.update { it.copy(query = value) }
    }

    fun toggleFavoritesOnly() {
        filter.update { it.copy(favoritesOnly = !it.favoritesOnly) }
    }

    fun updateSort(sort: TrackSort) {
        filter.update { it.copy(sort = sort) }
    }

    fun playAll(shuffle: Boolean) {
        val playlist = uiState.value.playlist ?: return
        if (playlist.tracks.isEmpty()) {
            message.value = "Esta playlist no tiene pistas importadas."
            return
        }
        val tracks = uiState.value.tracks
        if (tracks.isEmpty()) {
            message.value = "Ninguna pista coincide con el filtro actual."
            return
        }
        if (tracks.none { it.isPlayableInApp }) {
            message.value = "Ninguna pista de esta playlist se puede reproducir en la app. " +
                "Puedes abrirlas en su plataforma de origen."
            return
        }
        playerConnection.playTracks(tracks, shuffled = shuffle)
    }

    fun playTrack(track: Track) {
        if (!track.isPlayableInApp) {
            message.value = "Esta pista solo puede abrirse en la app externa oficial."
            return
        }
        val tracks = uiState.value.tracks
        val startIndex = tracks.indexOfFirst { it.id == track.id }
        if (startIndex < 0) {
            // La lista cambió (filtro u orden) justo antes del toque: se reproduce solo esa pista.
            playerConnection.playTracks(listOf(track), 0)
            return
        }
        playerConnection.playTracks(tracks, startIndex)
    }

    fun toggleFavorite(trackId: Long) {
        viewModelScope.launch {
            try {
                musicRepository.toggleFavorite(trackId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message.value = "No se ha podido actualizar el favorito."
            }
        }
    }

    fun clearMessage() {
        message.value = null
    }
}

private fun List<Track>.applyFilter(filter: TrackFilter): List<Track> {
    val query = filter.query.trim().normalizeForSearch()
    val filtered = if (query.isEmpty() && !filter.favoritesOnly) {
        this
    } else {
        filter { track ->
            val matchesQuery = query.isEmpty() ||
                track.title.normalizeForSearch().contains(query) ||
                track.artist.normalizeForSearch().contains(query)
            val matchesFavorite = !filter.favoritesOnly || track.isFavorite
            matchesQuery && matchesFavorite
        }
    }

    return when (filter.sort) {
        TrackSort.POSITION -> filtered.sortedBy { it.position }
        TrackSort.TITLE -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        TrackSort.DURATION -> filtered.sortedByDescending { it.durationMs ?: 0L }
    }
}
