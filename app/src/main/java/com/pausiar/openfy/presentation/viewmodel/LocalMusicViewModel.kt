package com.pausiar.openfy.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pausiar.openfy.data.repository.MusicRepository
import com.pausiar.openfy.domain.models.ImportResult
import com.pausiar.openfy.domain.models.PlaylistSummary
import com.pausiar.openfy.domain.usecases.ImportLocalTracksUseCase
import com.pausiar.openfy.domain.usecases.ObservePlaylistsUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LocalMusicUiState(
    val playlistName: String = "",
    val playlists: List<PlaylistSummary> = emptyList(),
    val message: String? = null,
)

class LocalMusicViewModel(
    observePlaylistsUseCase: ObservePlaylistsUseCase,
    private val importLocalTracksUseCase: ImportLocalTracksUseCase,
    private val musicRepository: MusicRepository,
) : ViewModel() {
    private val playlistName = MutableStateFlow("")
    private val message = MutableStateFlow<String?>(null)
    private val openPlaylistEvents = MutableSharedFlow<Long>()

    val uiState: StateFlow<LocalMusicUiState> = combine(
        observePlaylistsUseCase(),
        playlistName,
        message,
    ) { playlists, name, info ->
        LocalMusicUiState(
            playlistName = name,
            playlists = playlists.filter { it.isLocal },
            message = info,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalMusicUiState())

    val openPlaylist = openPlaylistEvents.asSharedFlow()

    fun updatePlaylistName(value: String) {
        playlistName.value = value
    }

    fun createEmptyPlaylist() {
        viewModelScope.launch {
            val playlistId = musicRepository.createLocalPlaylist(playlistName.value)
            message.value = "Playlist local creada."
            openPlaylistEvents.emit(playlistId)
        }
    }

    fun importUris(uris: List<Uri>) {
        viewModelScope.launch {
            when (val result = importLocalTracksUseCase(playlistName.value, uris)) {
                is ImportResult.Error -> message.value = result.message
                is ImportResult.Success -> {
                    message.value = result.message
                    openPlaylistEvents.emit(result.playlistId)
                }
            }
        }
    }

    fun clearMessage() {
        message.value = null
    }
}