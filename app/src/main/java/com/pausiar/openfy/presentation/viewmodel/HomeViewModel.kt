package com.pausiar.openfy.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pausiar.openfy.data.repository.MusicRepository
import com.pausiar.openfy.domain.models.ImportResult
import com.pausiar.openfy.domain.models.ParsedLink
import com.pausiar.openfy.domain.models.PlaylistSummary
import com.pausiar.openfy.domain.usecases.DetectLinkUseCase
import com.pausiar.openfy.domain.usecases.ImportPlaylistUseCase
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

data class HomeUiState(
    val inputUrl: String = "",
    val detectedLink: ParsedLink? = null,
    val isImporting: Boolean = false,
    val recentPlaylists: List<PlaylistSummary> = emptyList(),
    val message: String? = null,
)

class HomeViewModel(
    observePlaylistsUseCase: ObservePlaylistsUseCase,
    private val importPlaylistUseCase: ImportPlaylistUseCase,
    private val detectLinkUseCase: DetectLinkUseCase,
    private val musicRepository: MusicRepository,
) : ViewModel() {
    private val inputUrl = MutableStateFlow("")
    private val isImporting = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    // Canal en vez de SharedFlow: si la pantalla no esta suscrita en ese instante el evento no se pierde.
    private val openPlaylistEvents = Channel<Long>(Channel.BUFFERED)

    val uiState: StateFlow<HomeUiState> = combine(
        observePlaylistsUseCase(),
        inputUrl,
        isImporting,
        message,
    ) { playlists, url, importing, info ->
        HomeUiState(
            inputUrl = url,
            detectedLink = url.takeIf { it.isNotBlank() }?.let(detectLinkUseCase::invoke),
            isImporting = importing,
            recentPlaylists = playlists.take(5),
            message = info,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val openPlaylist: Flow<Long> = openPlaylistEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            try {
                musicRepository.seedDemoPlaylistIfNeeded()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (ignored: Exception) {
                // La playlist demo es opcional: un fallo aqui no debe tumbar la pantalla de inicio.
            }
        }
    }

    fun updateInput(value: String) {
        inputUrl.value = value
    }

    fun importUrl() {
        if (isImporting.value) return // evita imports duplicados por doble toque

        val url = inputUrl.value.trim()
        if (url.isBlank()) {
            message.value = "Pega primero un enlace de Spotify o YouTube."
            return
        }

        isImporting.value = true
        viewModelScope.launch {
            try {
                when (val result = importPlaylistUseCase(url)) {
                    is ImportResult.Error -> message.value = result.message
                    is ImportResult.Success -> {
                        inputUrl.value = ""
                        message.value = result.message
                        openPlaylistEvents.trySend(result.playlistId)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                message.value = "No se ha podido importar el enlace. Inténtalo de nuevo."
            } finally {
                isImporting.value = false
            }
        }
    }

    fun clearMessage() {
        message.value = null
    }
}
