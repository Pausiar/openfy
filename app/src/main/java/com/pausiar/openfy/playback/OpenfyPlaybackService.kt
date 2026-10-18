package com.pausiar.openfy.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.pausiar.openfy.MainActivity
import com.pausiar.openfy.OpenfyApplication

@OptIn(UnstableApi::class)
class OpenfyPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private lateinit var streamResolver: YouTubeStreamResolver

    private var consecutiveErrors = 0

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            prefetchUpcoming()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                consecutiveErrors = 0
                ensureCurrentItemFirstInShuffle()
                prefetchUpcoming()
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (shuffleModeEnabled) ensureCurrentItemFirstInShuffle()
            prefetchUpcoming()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            prefetchUpcoming()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) consecutiveErrors = 0
        }

        override fun onPlayerError(error: PlaybackException) {
            skipAfterError()
        }
    }

    override fun onCreate() {
        super.onCreate()

        streamResolver = (application as OpenfyApplication).appContainer.youtubeStreamResolver

        // Sin User-Agent fijo: cada peticion lleva las cabeceras adecuadas para su URL
        // (ver YouTubeHttp.playbackHeadersFor), anadidas por el ResolvingDataSource.
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(HTTP_CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(HTTP_READ_TIMEOUT_MS)

        val upstreamFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        val resolvingFactory = ResolvingDataSource.Factory(upstreamFactory, StreamUriResolver(streamResolver))

        val mediaSourceFactory = DefaultMediaSourceFactory(resolvingFactory)
            .setLoadErrorHandlingPolicy(StreamLoadErrorPolicy(streamResolver))

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_000,
            )
            .build()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(playerListener)
        exoPlayer = player

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(buildSessionActivity())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null ||
            !player.playWhenReady ||
            player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            // No hay nada sonando: liberamos la cola (quita la notificacion) y paramos el servicio.
            player?.stop()
            player?.clearMediaItems()
            stopSelf()
        }
    }

    override fun onDestroy() {
        exoPlayer?.removeListener(playerListener)
        mediaSession?.player?.release()
        mediaSession?.release()
        mediaSession = null
        exoPlayer = null
        super.onDestroy()
    }

    private fun buildSessionActivity(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Resuelve en segundo plano las 2 siguientes pistas para que los saltos sean inmediatos. */
    private fun prefetchUpcoming() {
        val player = exoPlayer ?: return
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return

        var index = player.currentMediaItemIndex
        repeat(PREFETCH_COUNT) {
            index = timeline.getNextWindowIndex(index, player.repeatMode.withoutRepeatOne(), player.shuffleModeEnabled)
            if (index == C.INDEX_UNSET || index == player.currentMediaItemIndex) return
            val uri = player.getMediaItemAt(index).localConfiguration?.uri ?: return@repeat
            streamResolver.prefetch(uri)
        }
    }

    /**
     * Con shuffle activo, ExoPlayer genera un orden aleatorio en el que la pista inicial puede
     * quedar en medio (y las anteriores no sonarian nunca sin repeticion). Reordenamos para
     * que la pista actual sea la primera y el resto vaya en orden aleatorio.
     */
    private fun ensureCurrentItemFirstInShuffle() {
        val player = exoPlayer ?: return
        if (!player.shuffleModeEnabled) return
        val count = player.mediaItemCount
        if (count <= 1) return
        val current = player.currentMediaItemIndex
        if (current !in 0 until count) return
        if (player.currentTimeline.getFirstWindowIndex(true) == current) return

        val order = IntArray(count)
        order[0] = current
        val rest = (0 until count).filter { it != current }.shuffled()
        rest.forEachIndexed { position, value -> order[position + 1] = value }
        player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(order, System.nanoTime()))
    }

    /** Ante un error de carga salta a la siguiente pista, con un limite de fallos seguidos. */
    private fun skipAfterError() {
        val player = exoPlayer ?: return
        consecutiveErrors++
        if (consecutiveErrors > MAX_CONSECUTIVE_ERRORS) {
            consecutiveErrors = 0
            return
        }
        if (!player.hasNextMediaItem()) return
        player.seekToNextMediaItem()
        player.prepare()
        player.playWhenReady = true
    }

    private fun Int.withoutRepeatOne(): Int =
        if (this == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ALL else this

    /** Traduce openfy-yt:// / openfy-search:// a la URL real, en el hilo de carga. */
    private class StreamUriResolver(
        private val resolver: YouTubeStreamResolver,
    ) : ResolvingDataSource.Resolver {
        override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
            val resolved = resolver.resolveUri(dataSpec.uri)
            if (resolved != null) {
                return dataSpec
                    .withUri(Uri.parse(resolved.url))
                    .withAdditionalHeaders(resolved.headers)
            }
            val scheme = dataSpec.uri.scheme
            return if (scheme == "http" || scheme == "https") {
                dataSpec.withAdditionalHeaders(YouTubeHttp.playbackHeadersFor(dataSpec.uri.toString()))
            } else {
                dataSpec
            }
        }
    }

    /**
     * - Los fallos de resolucion no se reintentan (ya se probaron todas las estrategias).
     * - Un 403/410 de googlevideo suele indicar URL caducada: se invalida la cache para que el
     *   reintento vuelva a resolver.
     */
    private class StreamLoadErrorPolicy(
        private val resolver: YouTubeStreamResolver,
    ) : DefaultLoadErrorHandlingPolicy() {
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            val error = loadErrorInfo.exception
            if (error.hasCause<StreamResolutionException>()) return C.TIME_UNSET

            val responseCode = error.findCause<HttpDataSource.InvalidResponseCodeException>()?.responseCode
            if (responseCode == 403 || responseCode == 410) {
                resolver.invalidate(loadErrorInfo.loadEventInfo.dataSpec.uri)
            }
            return super.getRetryDelayMsFor(loadErrorInfo)
        }

        private inline fun <reified T : Throwable> Throwable.findCause(): T? {
            var current: Throwable? = this
            var depth = 0
            while (current != null && depth < 8) {
                if (current is T) return current
                current = current.cause
                depth++
            }
            return null
        }

        private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean = findCause<T>() != null
    }

    private companion object {
        const val HTTP_CONNECT_TIMEOUT_MS = 10_000
        const val HTTP_READ_TIMEOUT_MS = 15_000
        const val PREFETCH_COUNT = 2
        const val MAX_CONSECUTIVE_ERRORS = 5
    }
}
