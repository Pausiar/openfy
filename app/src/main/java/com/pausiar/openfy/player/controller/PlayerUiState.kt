package com.pausiar.openfy.player.controller

import androidx.compose.runtime.Immutable
import com.pausiar.openfy.domain.models.RepeatPreference
import com.pausiar.openfy.domain.models.Track

/**
 * Estado "lento" del reproductor: solo cambia con eventos (cambio de pista, play/pausa,
 * buffering, modos...). NO cambia con el avance de la reproduccion; para eso usa
 * [PlaybackProgress] (PlayerConnection.progress / PlayerViewModel.progress).
 */
@Immutable
data class PlayerUiState(
    val currentTrack: Track? = null,
    /** Indice de la pista actual dentro de la cola (orden original, no el aleatorio). -1 si no hay. */
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    /** true mientras se prepara el inicio de la reproduccion o se esta cargando con play pulsado. */
    val isPreparing: Boolean = false,
    /** true si el reproductor esta en estado de buffering. */
    val isBuffering: Boolean = false,
    /** Duracion de la pista actual; se actualiza con eventos, no en cada tick. */
    val durationMs: Long = 0,
    /**
     * Posicion en el momento del ultimo evento (seek, pausa, cambio de pista...). NO avanza con
     * la reproduccion: usa [PlaybackProgress.positionMs] para mostrar el progreso.
     */
    val positionMs: Long = 0,
    val repeatPreference: RepeatPreference = RepeatPreference.OFF,
    val shuffleEnabled: Boolean = false,
    val errorMessage: String? = null,
)

/** Progreso de reproduccion; se publica ~2 veces por segundo solo mientras suena algo. */
@Immutable
data class PlaybackProgress(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
) {
    /** Fraccion reproducida en [0, 1]; 0 si la duracion es desconocida. */
    val fraction: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    /** Fraccion cargada en buffer en [0, 1]. */
    val bufferedFraction: Float
        get() = if (durationMs > 0L) {
            (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}
