package com.pausiar.openfy.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pausiar.openfy.data.repository.MusicRepository
import com.pausiar.openfy.domain.models.LibraryFilter
import com.pausiar.openfy.domain.models.Platform
import com.pausiar.openfy.domain.models.PlaylistSort
import com.pausiar.openfy.domain.models.PlaylistSummary
import com.pausiar.openfy.domain.usecases.ObservePlaylistsUseCase
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

data class LibraryUiState(
    val filter: LibraryFilter = LibraryFilter(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val message: String? = null,
)

class LibraryViewModel(
    observePlaylistsUseCase: ObservePlaylistsUseCase,
    private val musicRepository: MusicRepository,
) : ViewModel() {
    private val filter = MutableStateFlow(LibraryFilter())
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LibraryUiState> = combine(
        observePlaylistsUseCase(),
        filter,
        message,
    ) { playlists, activeFilter, info ->
        LibraryUiState(
            filter = activeFilter,
            playlists = playlists
                .filterBy(activeFilter)
                .sortedBy(activeFilter.sort),
            message = info,
        )
    }
        .flowOn(Dispatchers.Default) // filtrado y orden fuera del hilo principal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun updateQuery(value: String) {
        filter.update { it.copy(query = value) }
    }

    fun updatePlatform(platform: Platform?) {
        filter.update { it.copy(platform = platform) }
    }

    fun updateSort(sort: PlaylistSort) {
        filter.update { it.copy(sort = sort) }
    }

    fun renamePlaylist(playlistId: Long, title: String) {
        if (title.isBlank()) {
            message.value = "El nombre de la playlist no puede estar vacío."
            return
        }
        viewModelScope.launch {
            try {
                musicRepository.renamePlaylist(playlistId, title)
                message.value = "Playlist renombrada."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message.value = "No se ha podido renombrar la playlist."
            }
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            try {
                musicRepository.deletePlaylist(playlistId)
                message.value = "Playlist eliminada."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message.value = "No se ha podido eliminar la playlist."
            }
        }
    }

    fun clearMessage() {
        message.value = null
    }
}

private fun List<PlaylistSummary>.filterBy(filter: LibraryFilter): List<PlaylistSummary> {
    val query = filter.query.trim().normalizeForSearch()
    if (query.isEmpty() && filter.platform == null) return this
    return filter { playlist ->
        val matchesQuery = query.isEmpty() ||
            playlist.title.normalizeForSearch().contains(query) ||
            playlist.description.orEmpty().normalizeForSearch().contains(query)
        val matchesPlatform = filter.platform == null || playlist.platform == filter.platform
        matchesQuery && matchesPlatform
    }
}

private fun List<PlaylistSummary>.sortedBy(sort: PlaylistSort): List<PlaylistSummary> = when (sort) {
    PlaylistSort.RECENT -> sortedByDescending { it.updatedAt }
    PlaylistSort.TITLE -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    PlaylistSort.TRACK_COUNT -> sortedByDescending { it.trackCount }
}
