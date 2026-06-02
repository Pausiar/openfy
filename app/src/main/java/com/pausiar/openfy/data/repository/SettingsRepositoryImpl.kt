package com.pausiar.openfy.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pausiar.openfy.domain.models.RepeatPreference
import com.pausiar.openfy.domain.models.SettingsState
import com.pausiar.openfy.domain.models.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "openfy_settings")

class SettingsRepositoryImpl(
    private val context: Context,
) : SettingsRepository {

    override val settings: Flow<SettingsState> = context.settingsDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            SettingsState(
                themeMode = ThemeMode.entries.firstOrNull { it.name == preferences[Keys.themeMode] } ?: ThemeMode.DARK,
                autoplayEnabled = preferences[Keys.autoplayEnabled] ?: true,
                repeatPreference = RepeatPreference.entries.firstOrNull {
                    it.name == preferences[Keys.repeatMode]
                } ?: RepeatPreference.OFF,
                shuffleEnabled = preferences[Keys.shuffleEnabled] ?: false,
                lastPlaylistId = preferences[Keys.lastPlaylistId],
            )
        }

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.themeMode] = themeMode.name }
    }

    override suspend fun setAutoplay(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.autoplayEnabled] = enabled }
    }

    override suspend fun setRepeatMode(repeatPreference: RepeatPreference) {
        context.settingsDataStore.edit { it[Keys.repeatMode] = repeatPreference.name }
    }

    override suspend fun setShuffle(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.shuffleEnabled] = enabled }
    }

    override suspend fun setLastPlaylistId(playlistId: Long?) {
        context.settingsDataStore.edit { preferences ->
            if (playlistId == null) {
                preferences.remove(Keys.lastPlaylistId)
            } else {
                preferences[Keys.lastPlaylistId] = playlistId
            }
        }
    }

    override suspend fun overwrite(settingsState: SettingsState) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.themeMode] = settingsState.themeMode.name
            preferences[Keys.autoplayEnabled] = settingsState.autoplayEnabled
            preferences[Keys.repeatMode] = settingsState.repeatPreference.name
            preferences[Keys.shuffleEnabled] = settingsState.shuffleEnabled
            if (settingsState.lastPlaylistId == null) {
                preferences.remove(Keys.lastPlaylistId)
            } else {
                preferences[Keys.lastPlaylistId] = settingsState.lastPlaylistId
            }
        }
    }

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val autoplayEnabled = booleanPreferencesKey("autoplay_enabled")
        val repeatMode = stringPreferencesKey("repeat_mode")
        val shuffleEnabled = booleanPreferencesKey("shuffle_enabled")
        val lastPlaylistId = longPreferencesKey("last_playlist_id")
    }
}