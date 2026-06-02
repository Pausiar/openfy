package com.pausiar.openfy.data.repository

import com.pausiar.openfy.domain.models.RepeatPreference
import com.pausiar.openfy.domain.models.SettingsState
import com.pausiar.openfy.domain.models.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<SettingsState>
    suspend fun setThemeMode(themeMode: ThemeMode)
    suspend fun setAutoplay(enabled: Boolean)
    suspend fun setRepeatMode(repeatPreference: RepeatPreference)
    suspend fun setShuffle(enabled: Boolean)
    suspend fun setLastPlaylistId(playlistId: Long?)
    suspend fun overwrite(settingsState: SettingsState)
}