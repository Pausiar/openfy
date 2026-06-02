package com.pausiar.openfy.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pausiar.openfy.data.repository.SettingsRepository
import com.pausiar.openfy.domain.models.RepeatPreference
import com.pausiar.openfy.domain.models.Track
import com.pausiar.openfy.player.controller.PlayerConnection
import com.pausiar.openfy.player.controller.PlayerUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlayerScreenUiState(
    val player: PlayerUiState = PlayerUiState(),
    val queue: List<Track> = emptyList(),
)

class PlayerViewModel(
    private val playerConnection: PlayerConnection,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<PlayerScreenUiState> = combine(
        playerConnection.uiState,
        playerConnection.queue,
    ) { player, queue ->
        PlayerScreenUiState(player = player, queue = queue)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerScreenUiState())

    fun togglePlayPause() = playerConnection.togglePlayPause()
    fun skipNext() = playerConnection.skipNext()
    fun skipPrevious() = playerConnection.skipPrevious()
    fun seekTo(positionMs: Long) = playerConnection.seekTo(positionMs)
    fun clearError() = playerConnection.clearError()

    fun cycleRepeatMode() {
        val next = when (uiState.value.player.repeatPreference) {
            RepeatPreference.OFF -> RepeatPreference.ALL
            RepeatPreference.ALL -> RepeatPreference.ONE
            RepeatPreference.ONE -> RepeatPreference.OFF
        }
        viewModelScope.launch { settingsRepository.setRepeatMode(next) }
        playerConnection.setRepeatMode(next)
    }

    fun toggleShuffle() {
        val next = !uiState.value.player.shuffleEnabled
        viewModelScope.launch { settingsRepository.setShuffle(next) }
        playerConnection.setShuffle(next)
    }
}