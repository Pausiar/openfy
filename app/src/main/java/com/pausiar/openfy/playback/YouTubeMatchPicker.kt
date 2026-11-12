package com.pausiar.openfy.playback

import kotlin.math.abs

/** Ordena los resultados de busqueda de YouTube para evitar Shorts, clips y teasers. */
object YouTubeMatchPicker {
    private const val MAX_CANDIDATES = 3
    private const val TOLERANCE_MS = 10_000L
    private const val MIN_FULL_SONG_SEC = 60L

    /** [durationSec] <= 0 significa duracion desconocida. */
    data class Candidate(val videoId: String, val durationSec: Long, val isShort: Boolean)

    fun pick(candidates: List<Candidate>, expectedDurationMs: Long?): List<String> {
        val valid = candidates.filter { !it.isShort }
        val expected = expectedDurationMs?.takeIf { it > 0 }
        val ordered = if (expected == null) {
            valid.filter { it.durationSec <= 0 || it.durationSec >= MIN_FULL_SONG_SEC }
        } else {
            val (near, rest) = valid
                .filter { it.durationSec <= 0 || it.durationSec * 1000 in expected * 6 / 10..expected * 2 }
                .partition { it.durationSec > 0 && abs(it.durationSec * 1000 - expected) <= TOLERANCE_MS }
            near + rest
        }
        return ordered.map { it.videoId }.distinct().take(MAX_CANDIDATES)
    }
}
