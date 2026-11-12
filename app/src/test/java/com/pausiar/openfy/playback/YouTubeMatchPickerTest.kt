package com.pausiar.openfy.playback

import com.pausiar.openfy.playback.YouTubeMatchPicker.Candidate
import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeMatchPickerTest {

    private fun c(id: String, sec: Long, short: Boolean = false) = Candidate(id, sec, short)

    @Test
    fun shortsAreNeverPicked() {
        val result = YouTubeMatchPicker.pick(listOf(c("s", 200, short = true), c("a", 201)), 200_000L)
        assertEquals(listOf("a"), result)
    }

    @Test
    fun closeDurationsComeFirstAndOutliersDropped() {
        val result = YouTubeMatchPicker.pick(
            listOf(c("a", 25), c("b", 260), c("c", 201)),
            200_000L,
        )
        assertEquals(listOf("c", "b"), result)
    }

    @Test
    fun hourLongVideoIsDropped() {
        val result = YouTubeMatchPicker.pick(listOf(c("long", 3600)), 200_000L)
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun unknownExpectedDurationKeepsFullLengthAndUnknownOnes() {
        val result = YouTubeMatchPicker.pick(listOf(c("a", 20), c("b", 0), c("c", 180)), null)
        assertEquals(listOf("b", "c"), result)
    }

    @Test
    fun returnsAtMostThreeCandidates() {
        val result = YouTubeMatchPicker.pick(
            listOf(c("1", 200), c("2", 201), c("3", 202), c("4", 203), c("5", 204)),
            200_000L,
        )
        assertEquals(3, result.size)
    }

    @Test
    fun emptyInputGivesEmptyOutput() {
        assertEquals(emptyList<String>(), YouTubeMatchPicker.pick(emptyList(), 200_000L))
    }
}
