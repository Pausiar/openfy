package com.pausiar.openfy.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pausiar.openfy.data.repository.MusicRepository
import com.pausiar.openfy.domain.models.ImportResult
import com.pausiar.openfy.domain.models.PlaylistSummary
import com.pausiar.openfy.domain.usecases.ImportLocalTracksUseCase
import com.pausiar.openfy.domain.usecases.ObservePlaylistsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LocalMusicUiState(
    val playlistName: String = "",
    val playlists: List<PlaylistSummary> = emptyList(),
    val message: String? = null,
    /** true mientras se leen los metadatos de los archivos seleccionados. */
    val isImporting: Boolean = false,
)

class LocalMusicViewModel(
    observePlaylistsUseCase: ObservePlaylistsUseCase,
    private val importLocalTracksUseCase: ImportLocalTracksUseCase,
    private val musicRepository: MusicRepository,
) : ViewModel() {
    private val playlistName = MutableStateFlow("")
    private val message = MutableStateFlow<String?>(null)
    private val isImporting = MutableStateFlow(false)

    // Canal en vez de SharedFlow: el evento no se pierde si la pantalla no esta suscrita justo entonces.
    private val openPlaylistEvents = Channel<Long>(Channel.BUFFERED)

    val uiState: StateFlow<LocalMusicUiState> = combine(
        observePlaylistsUseCase(),
        playlistName,
        message,
        isImporting,
    ) { playlists, name, info, importing ->
        LocalMusicUiState(
            playlistName = name,
            playlists = playlists.filter { it.isLocal },
            message = info,
            isImporting = importing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalMusicUiState())

    val openPlaylist: Flow<Long> = openPlaylistEvents.receiveAsFlow()

    fun updatePlaylistName(value: String) {
        playlistName.value = value
    }

    fun createEmptyPlaylist() {
        viewModelScope.launch {
            try {
                val playlistId = musicRepository.createLocalPlaylist(playlistName.value)
                message.value = "Playlist local creada."
                openPlaylistEvents.trySend(playlistId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message.value = "No se ha podido crear la playlist."
            }
        }
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty() || isImporting.value) return

        isImporting.value = true
        viewModelScope.launch {
            try {
                when (val result = importLocalTracksUseCase(playlistName.value, uris)) {
                    is ImportResult.Error -> message.value = result.message
                    is ImportResult.Success -> {
                        message.value = result.message
                        openPlaylistEvents.trySend(result.playlistId)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message.value = "No se han podido importar los archivos de audio."
            } finally {
                isImporting.value = false
            }
        }
    }

    fun clearMessage() {
        message.value = null
    }
}
