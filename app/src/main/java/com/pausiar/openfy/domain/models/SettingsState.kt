package com.pausiar.openfy.domain.models

data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val autoplayEnabled: Boolean = true,
    val repeatPreference: RepeatPreference = RepeatPreference.OFF,
    val shuffleEnabled: Boolean = false,
    val lastPlaylistId: Long? = null,
)