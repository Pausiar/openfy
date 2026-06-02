package com.pausiar.openfy.player.controller

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.pausiar.openfy.data.repository.MusicRepository
import com.pausiar.openfy.data.repository.SettingsRepository
import com.pausiar.openfy.domain.models.RepeatPreference
import com.pausiar.openfy.domain.models.SettingsState
import com.pausiar.openfy.domain.models.Track
import com.pausiar.openfy.playback.OpenfyPlaybackService
import com.pausiar.openfy.playback.YouTubeStreamResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerConnection(
    appContext: Context,
    private val musicRepository: MusicRepository,
    settingsRepository: SettingsRepository,
    private val youtubeStreamResolver: YouTubeStreamResolver,
) {
    private val applicationContext = appContext.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _controller = MutableStateFlow<MediaController?>(null)
    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    private val _uiState = MutableStateFlow(PlayerUiState())

    val queue: StateFlow<List<Track>> = _queue.asStateFlow()
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refreshState(player)
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _uiState.value = _uiState.value.copy(
                errorMessage = error.localizedMessage ?: "No se ha podido reproducir este contenido.",
            )
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val trackId = mediaItem?.mediaId?.toLongOrNull() ?: return
            scope.launch { musicRepository.logPlayback(trackId, progressMs = 0L) }
        }
    }

    init {
        connectController()
        scope.launch {
            settingsRepository.settings.collect(::applySettings)
        }
        scope.launch {
            while (isActive) {
                _controller.value?.let(::refreshState)
                delay(500)
            }
        }
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0, shuffled: Boolean = false) {
        val candidateTracks = tracks.filter { it.isPlayableInApp && !it.playbackUri.isNullOrBlank() }
        val startTrack = tracks.getOrNull(startIndex)

        if (candidateTracks.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Esta playlist no tiene contenido reproducible dentro de Openfy.",
            )
            return
        }

        scope.launch {
            _uiState.value = _uiState.value.copy(errorMessage = null)
            val resolvedTracks = withContext(Dispatchers.IO) {
                val startTrack = tracks.getOrNull(startIndex)
                val prioritized = buildList {
                    startTrack?.let { add(it) }
                    candidateTracks.filterNot { track -> track.id == startTrack?.id }.forEach(::add)
                }

                prioritized.mapNotNull { track ->
                    val resolvedUri = youtubeStreamResolver.resolvePlaybackUri(track) ?: return@mapNotNull null
                    track.copyForPlayback(resolvedUri)
                }
            }

            if (resolvedTracks.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "No se ha podido preparar la reproduccion. Comprueba tu conexion e intentalo otra vez.",
                )
                return@launch
            }

            val controller = _controller.value
            if (controller == null) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "El reproductor aun no esta listo. Intentalo de nuevo en unos segundos.",
                )
                return@launch
            }

            val resolvedStartIndex = resolvedTracks.indexOfFirst { it.track.id == startTrack?.id }.coerceAtLeast(0)
            _queue.value = resolvedTracks.map { it.track }
            controller.setMediaItems(
                resolvedTracks.map { it.toMediaItem() },
                resolvedStartIndex,
                C.TIME_UNSET,
            )
            controller.shuffleModeEnabled = shuffled
            controller.prepare()
            controller.playWhenReady = true
            controller.play()
            refreshState(controller)
        }
    }

    fun togglePlayPause() {
        val controller = _controller.value ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
        refreshState(controller)
    }

    fun skipNext() {
        val controller = _controller.value ?: return
        controller.seekToNextMediaItem()
        refreshState(controller)
    }

    fun skipPrevious() {
        val controller = _controller.value ?: return
        controller.seekToPreviousMediaItem()
        refreshState(controller)
    }

    fun seekTo(positionMs: Long) {
        val controller = _controller.value ?: return
        controller.seekTo(positionMs)
        refreshState(controller)
    }

    fun setRepeatMode(preference: RepeatPreference) {
        _controller.value?.repeatMode = preference.toPlayerRepeatMode()
        _controller.value?.let(::refreshState)
    }

    fun setShuffle(enabled: Boolean) {
        _controller.value?.shuffleModeEnabled = enabled
        _controller.value?.let(::refreshState)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun connectController() {
        val sessionToken = SessionToken(
            applicationContext,
            ComponentName(applicationContext, OpenfyPlaybackService::class.java),
        )
        val future = MediaController.Builder(applicationContext, sessionToken).buildAsync()
        future.addListener(
            {
                val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
                controller.addListener(listener)
                _controller.value = controller
                refreshState(controller)
            },
            ContextCompat.getMainExecutor(applicationContext),
        )
    }

    private fun applySettings(settingsState: SettingsState) {
        val controller = _controller.value ?: return
        controller.repeatMode = settingsState.repeatPreference.toPlayerRepeatMode()
        controller.shuffleModeEnabled = settingsState.shuffleEnabled
        refreshState(controller)
    }

    private fun refreshState(player: Player) {
        val currentTrack = _queue.value.firstOrNull { it.id == player.currentMediaItem?.mediaId?.toLongOrNull() }
        _uiState.value = _uiState.value.copy(
            currentTrack = currentTrack,
            isPlaying = player.isPlaying,
            durationMs = player.duration.takeIf { it >= 0 } ?: 0L,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            repeatPreference = player.repeatMode.toRepeatPreference(),
            shuffleEnabled = player.shuffleModeEnabled,
        )
    }
}

private fun Track.copyForPlayback(resolvedUri: String): ResolvedTrack = ResolvedTrack(this, resolvedUri)

private data class ResolvedTrack(
    val track: Track,
    val resolvedUri: String,
)

private fun ResolvedTrack.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(track.id.toString())
    .setUri(resolvedUri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.imageUrl?.let(android.net.Uri::parse))
            .build()
    )
    .build()

private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(playbackUri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(imageUrl?.let(android.net.Uri::parse))
            .build()
    )
    .build()

private fun RepeatPreference.toPlayerRepeatMode(): Int = when (this) {
    RepeatPreference.OFF -> Player.REPEAT_MODE_OFF
    RepeatPreference.ONE -> Player.REPEAT_MODE_ONE
    RepeatPreference.ALL -> Player.REPEAT_MODE_ALL
}

private fun Int.toRepeatPreference(): RepeatPreference = when (this) {
    Player.REPEAT_MODE_ONE -> RepeatPreference.ONE
    Player.REPEAT_MODE_ALL -> RepeatPreference.ALL
    else -> RepeatPreference.OFF
}