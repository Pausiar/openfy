package com.pausiar.openfy.player.controller

import com.pausiar.openfy.domain.models.RepeatPreference
import com.pausiar.openfy.domain.models.Track

data class PlayerUiState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isPreparing: Boolean = false,
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val repeatPreference: RepeatPreference = RepeatPreference.OFF,
    val shuffleEnabled: Boolean = false,
    val errorMessage: String? = null,
)