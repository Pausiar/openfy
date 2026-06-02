package com.pausiar.openfy.presentation.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pausiar.openfy.OpenfyApplication

object OpenfyViewModelFactory {
    fun homeFactory(): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val container = container(this)
            HomeViewModel(
                observePlaylistsUseCase = container.observePlaylistsUseCase,
                importPlaylistUseCase = container.importPlaylistUseCase,
                detectLinkUseCase = container.detectLinkUseCase,
                musicRepository = container.musicRepository,
            )
        }
    }

    fun libraryFactory(): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val container = container(this)
            LibraryViewModel(
                observePlaylistsUseCase = container.observePlaylistsUseCase,
                musicRepository = container.musicRepository,
            )
        }
    }

    fun playlistDetailFactory(playlistId: Long): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val container = container(this)
            PlaylistDetailViewModel(
                playlistId = playlistId,
                observePlaylistDetailUseCase = container.observePlaylistDetailUseCase,
                musicRepository = container.musicRepository,
                playerConnection = container.playerConnection,
                settingsRepository = container.settingsRepository,
            )
        }
    }

    fun playerFactory(): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val container = container(this)
            PlayerViewModel(
                playerConnection = container.playerConnection,
                settingsRepository = container.settingsRepository,
            )
        }
    }

    fun localMusicFactory(): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val container = container(this)
            LocalMusicViewModel(
                observePlaylistsUseCase = container.observePlaylistsUseCase,
                importLocalTracksUseCase = container.importLocalTracksUseCase,
                musicRepository = container.musicRepository,
            )
        }
    }

    fun settingsFactory(): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val container = container(this)
            SettingsViewModel(
                settingsRepository = container.settingsRepository,
                musicRepository = container.musicRepository,
                exportLibraryUseCase = container.exportLibraryUseCase,
                importLibraryDumpUseCase = container.importLibraryDumpUseCase,
            )
        }
    }

    private fun container(extras: CreationExtras) =
        (extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as OpenfyApplication).appContainer
}