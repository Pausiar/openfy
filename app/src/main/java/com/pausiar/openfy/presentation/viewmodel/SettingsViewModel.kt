package com.pausiar.openfy.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pausiar.openfy.data.repository.MusicRepository
import com.pausiar.openfy.data.repository.SettingsRepository
import com.pausiar.openfy.domain.models.RepeatPreference
import com.pausiar.openfy.domain.models.SettingsState
import com.pausiar.openfy.domain.models.ThemeMode
import com.pausiar.openfy.domain.usecases.ExportLibraryUseCase
import com.pausiar.openfy.domain.usecases.ImportLibraryDumpUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: SettingsState = SettingsState(),
    val message: String? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val musicRepository: MusicRepository,
    private val exportLibraryUseCase: ExportLibraryUseCase,
    private val importLibraryDumpUseCase: ImportLibraryDumpUseCase,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        message,
    ) { settings, info ->
        SettingsUiState(settings = settings, message = info)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun updateTheme(themeMode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(themeMode) }
    }

    fun updateAutoplay(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoplay(enabled) }
    }

    fun updateRepeat(repeatPreference: RepeatPreference) {
        viewModelScope.launch { settingsRepository.setRepeatMode(repeatPreference) }
    }

    fun updateShuffle(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShuffle(enabled) }
    }

    suspend fun exportBackup(): Result<String> = runCatching { exportLibraryUseCase() }

    suspend fun importBackup(raw: String): Result<Unit> = importLibraryDumpUseCase(raw).onSuccess {
        message.value = "Biblioteca restaurada correctamente."
    }.onFailure {
        message.value = it.message ?: "No se ha podido importar el archivo."
    }

    fun deleteAllData() {
        viewModelScope.launch {
            musicRepository.replaceLibrary(emptyList(), emptyList(), emptyList())
            settingsRepository.overwrite(SettingsState())
            message.value = "Todos los datos locales han sido borrados."
        }
    }

    fun clearMessage() {
        message.value = null
    }
}