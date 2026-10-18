package com.pausiar.openfy.player.controller

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.pausiar.openfy.data.repository.MusicRepository
import com.pausiar.openfy.data.repository.SettingsRepository
import com.pausiar.openfy.domain.models.RepeatPreference
import com.pausiar.openfy.domain.models.Track
import com.pausiar.openfy.playback.OpenfyPlaybackService
import com.pausiar.openfy.playback.PlaybackUris
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Puente entre la UI y el [OpenfyPlaybackService] (via MediaController).
 *
 * La cola se construye al instante con MediaItems ligeros (URIs `openfy-yt://` /
 * `openfy-search://`); la URL real se resuelve de forma perezosa en el servicio.
 *
 * - [uiState]: estado que solo cambia con eventos (pista, play/pausa, buffering, modos, error).
 * - [progress]: posicion/duracion/buffer, publicado periodicamente solo mientras suena algo.
 */
class PlayerConnection(
    appContext: Context,
    private val musicRepository: MusicRepository,
    settingsRepository: SettingsRepository,
) {
    private val applicationContext = appContext.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _controller = MutableStateFlow<MediaController?>(null)
    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    private val _uiState = MutableStateFlow(PlayerUiState())
    private val _progress = MutableStateFlow(PlaybackProgress())

    val queue: StateFlow<List<Track>> = _queue.asStateFlow()
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()

    private var connecting = false
    private var awaitingController = false
    private var progressJob: Job? = null
    private var latestModes: PlaybackModes? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val positionRelevant = events.containsAny(
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_TIMELINE_CHANGED,
            )
            refreshState(player, updatePosition = positionRelevant)
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.update { it.copy(errorMessage = errorMessageFor(error)) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val trackId = mediaItem?.mediaId?.toLongOrNull() ?: return
            scope.launch {
                runCatching { musicRepository.logPlayback(trackId, progressMs = 0L) }
            }
        }
    }

    init {
        connectController()
        scope.launch {
            settingsRepository.settings
                .map { PlaybackModes(it.repeatPreference, it.shuffleEnabled) }
                .distinctUntilChanged()
                .collect { modes ->
                    latestModes = modes
                    _controller.value?.let { applyModes(it, modes) }
                }
        }
    }

    /**
     * Reproduce [tracks] empezando por [startIndex] (indice dentro de [tracks]). Si [startIndex]
     * es null se empieza por la primera pista reproducible, o por una aleatoria si [shuffled].
     */
    fun playTracks(tracks: List<Track>, startIndex: Int? = null, shuffled: Boolean = false) {
        val entries = tracks.mapNotNull { track ->
            PlaybackUris.forTrack(track)?.let { uri -> QueueEntry(track, uri) }
        }

        if (entries.isEmpty()) {
            _uiState.update {
                it.copy(errorMessage = "Esta playlist no tiene contenido reproducible dentro de Openfy.")
            }
            return
        }

        val requestedId = startIndex?.let { tracks.getOrNull(it)?.id }
        val requestedPosition = requestedId?.let { id -> entries.indexOfFirst { it.track.id == id } } ?: -1
        val startPosition = when {
            requestedPosition >= 0 -> requestedPosition
            shuffled -> Random.nextInt(entries.size)
            else -> 0
        }
        val queueTracks = entries.map { it.track }
        val mediaItems = entries.map { it.toMediaItem() }

        scope.launch {
            _uiState.update { it.copy(errorMessage = null, isPreparing = true) }

            awaitingController = true
            val controller = awaitController()
            awaitingController = false
            if (controller == null) {
                _uiState.update {
                    it.copy(
                        isPreparing = false,
                        errorMessage = "El reproductor aún no está listo. Inténtalo de nuevo en unos segundos.",
                    )
                }
                return@launch
            }

            _queue.value = queueTracks
            controller.setMediaItems(mediaItems, startPosition, C.TIME_UNSET)
            // El orden aleatorio lo gestiona el reproductor (el servicio pone la pista inicial
            // la primera); la cola se mantiene en su orden original.
            controller.shuffleModeEnabled = shuffled
            controller.prepare()
            controller.play()
            refreshState(controller, updatePosition = true)
        }
    }

    fun togglePlayPause() {
        val controller = _controller.value ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE && controller.mediaItemCount > 0) {
                controller.prepare()
            }
            controller.play()
        }
        refreshState(controller, updatePosition = true)
    }

    fun skipNext() {
        val controller = _controller.value ?: return
        if (!controller.hasNextMediaItem()) return
        controller.seekToNextMediaItem()
        prepareIfIdle(controller)
        refreshState(controller, updatePosition = true)
    }

    /** Si han pasado mas de ~3 s vuelve al inicio de la pista; si no, a la anterior. */
    fun skipPrevious() {
        val controller = _controller.value ?: return
        controller.seekToPrevious()
        prepareIfIdle(controller)
        refreshState(controller, updatePosition = true)
    }

    fun seekTo(positionMs: Long) {
        val controller = _controller.value ?: return
        controller.seekTo(positionMs.coerceAtLeast(0L))
        refreshState(controller, updatePosition = true)
    }

    /** Salta a la pista [index] de [queue] (indice en el orden original de la cola). */
    fun playQueueItem(index: Int) {
        val controller = _controller.value ?: return
        if (index !in 0 until controller.mediaItemCount) return
        controller.seekToDefaultPosition(index)
        prepareIfIdle(controller)
        controller.play()
        refreshState(controller, updatePosition = true)
    }

    fun setRepeatMode(preference: RepeatPreference) {
        val controller = _controller.value ?: return
        controller.repeatMode = preference.toPlayerRepeatMode()
        refreshState(controller, updatePosition = false)
    }

    fun setShuffle(enabled: Boolean) {
        val controller = _controller.value ?: return
        controller.shuffleModeEnabled = enabled
        refreshState(controller, updatePosition = false)
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun prepareIfIdle(controller: MediaController) {
        if (controller.playbackState == Player.STATE_IDLE && controller.mediaItemCount > 0) {
            controller.prepare()
            controller.playWhenReady = true
        }
    }

    private suspend fun awaitController(maxAttempts: Int = 40): MediaController? {
        if (_controller.value == null) connectController()
        repeat(maxAttempts) {
            _controller.value?.let { return it }
            delay(125)
        }
        return _controller.value
    }

    private fun connectController() {
        if (connecting || _controller.value != null) return
        connecting = true

        val sessionToken = SessionToken(
            applicationContext,
            ComponentName(applicationContext, OpenfyPlaybackService::class.java),
        )
        val future = MediaController.Builder(applicationContext, sessionToken)
            .setListener(
                object : MediaController.Listener {
                    override fun onDisconnected(controller: MediaController) {
                        if (_controller.value === controller) {
                            controller.removeListener(listener)
                            _controller.value = null
                            stopProgressTicker()
                            _uiState.update { it.copy(isPlaying = false, isBuffering = false, isPreparing = false) }
                        }
                    }
                }
            )
            .buildAsync()
        future.addListener(
            {
                connecting = false
                val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
                controller.addListener(listener)
                _controller.value = controller
                latestModes?.let { applyModes(controller, it) }
                refreshState(controller, updatePosition = true)
            },
            ContextCompat.getMainExecutor(applicationContext),
        )
    }

    private fun applyModes(controller: MediaController, modes: PlaybackModes) {
        controller.repeatMode = modes.repeatPreference.toPlayerRepeatMode()
        controller.shuffleModeEnabled = modes.shuffleEnabled
        refreshState(controller, updatePosition = false)
    }

    private fun refreshState(player: Player, updatePosition: Boolean) {
        val queue = _queue.value
        val mediaId = player.currentMediaItem?.mediaId
        val playerIndex = player.currentMediaItemIndex
        val currentIndex = when {
            mediaId == null -> -1
            queue.getOrNull(playerIndex)?.id?.toString() == mediaId -> playerIndex
            else -> queue.indexOfFirst { it.id.toString() == mediaId }
        }
        val currentTrack = queue.getOrNull(currentIndex)
        val playbackState = player.playbackState
        val isBuffering = playbackState == Player.STATE_BUFFERING
        val durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            ?: currentTrack?.durationMs
            ?: 0L

        _uiState.update { current ->
            current.copy(
                currentTrack = currentTrack,
                currentIndex = currentIndex,
                isPlaying = player.isPlaying,
                isBuffering = isBuffering,
                isPreparing = awaitingController || (isBuffering && player.playWhenReady),
                durationMs = durationMs,
                positionMs = if (updatePosition) player.currentPosition.coerceAtLeast(0L) else current.positionMs,
                repeatPreference = player.repeatMode.toRepeatPreference(),
                shuffleEnabled = player.shuffleModeEnabled,
            )
        }

        publishProgress(player)
        if (player.isPlaying) startProgressTicker(player) else stopProgressTicker()
    }

    private fun publishProgress(player: Player) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            ?: _uiState.value.currentTrack?.durationMs
            ?: 0L
        _progress.value = PlaybackProgress(
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
        )
    }

    private fun startProgressTicker(player: Player) {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (isActive) {
                publishProgress(player)
                delay(PROGRESS_INTERVAL_MS)
            }
        }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun errorMessageFor(error: PlaybackException): String {
        val title = _uiState.value.currentTrack?.title
        val base = if (title.isNullOrBlank()) {
            "No se ha podido reproducir esta canción."
        } else {
            "No se ha podido reproducir \"$title\"."
        }
        val networkIssue = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        val hasNext = _controller.value?.hasNextMediaItem() == true
        return when {
            networkIssue -> "$base Comprueba tu conexión."
            hasNext -> "$base Pasando a la siguiente."
            else -> base
        }
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 500L
    }
}

private data class PlaybackModes(
    val repeatPreference: RepeatPreference,
    val shuffleEnabled: Boolean,
)

private data class QueueEntry(
    val track: Track,
    val uri: String,
)

private fun QueueEntry.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(track.id.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.imageUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse))
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
