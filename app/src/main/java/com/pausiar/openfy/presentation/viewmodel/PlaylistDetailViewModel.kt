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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistDetailUiState())

    init {
        viewModelScope.launch { settingsRepository.setLastPlaylistId(playlistId) }
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
        val tracks = uiState.value.tracks.ifEmpty { playlist.tracks }
        if (tracks.isEmpty()) {
            message.value = "Esta playlist no tiene pistas importadas."
            return
        }
        playerConnection.playTracks(tracks, shuffled = shuffle)
    }

    fun playTrack(track: Track) {
        val tracks = uiState.value.tracks
        if (!track.isPlayableInApp) {
            message.value = "Esta pista solo puede abrirse en la app externa oficial."
            return
        }
        playerConnection.playTracks(tracks, tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
    }

    fun toggleFavorite(trackId: Long) {
        viewModelScope.launch { musicRepository.toggleFavorite(trackId) }
    }

    fun clearMessage() {
        message.value = null
    }
}

private fun List<Track>.applyFilter(filter: TrackFilter): List<Track> {
    val filtered = filter { track ->
        val matchesQuery = filter.query.isBlank() ||
            track.title.contains(filter.query.trim(), ignoreCase = true) ||
            track.artist.contains(filter.query.trim(), ignoreCase = true)
        val matchesFavorite = !filter.favoritesOnly || track.isFavorite
        matchesQuery && matchesFavorite
    }

    return when (filter.sort) {
        TrackSort.POSITION -> filtered.sortedBy { it.position }
        TrackSort.TITLE -> filtered.sortedBy { it.title.lowercase() }
        TrackSort.DURATION -> filtered.sortedByDescending { it.durationMs ?: 0L }
    }
}