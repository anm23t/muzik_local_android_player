package org.muzzzlo.player.core.playback

import android.media.AudioManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CrossfadeTimingTest {
    @ParameterizedTest(name = "{0} ms track, {1} ms overlap")
    @CsvSource(
        // duration, overlap, prepare at, start at
        "240000, 5000, 232000, 235000",
        "240000, 12000, 225000, 228000",
        // The next track is loaded at once when the overlap starts within the first seconds.
        "6000, 3000, 0, 3000",
        "8000, 4000, 1000, 4000",
    )
    fun `overlap ends with the track and loading comes first`(
        durationMs: Long,
        overlapMs: Long,
        prepareAtMs: Long,
        startAtMs: Long,
    ) {
        assertEquals(CrossfadeTiming(prepareAtMs, startAtMs, overlapMs), CrossfadeTiming.of(durationMs, overlapMs))
    }

    @ParameterizedTest(name = "{0} ms track, {1} ms overlap")
    @CsvSource("240000, 0", "240000, -1", "5000, 5000", "4000, 5000", "0, 5000")
    fun `no timing without an overlap that fits the track`(
        durationMs: Long,
        overlapMs: Long,
    ) {
        assertNull(CrossfadeTiming.of(durationMs, overlapMs))
    }

    @ParameterizedTest(name = "position {0} ms of {1} ms")
    @CsvSource(
        "0, 5000, 0",
        "2500, 5000, 0.5",
        "5000, 5000, 1",
        "7000, 5000, 1",
        "-20, 5000, 0",
        "100, 0, 1",
    )
    fun `fade progress follows the incoming track`(
        positionMs: Long,
        overlapMs: Long,
        expected: Float,
    ) {
        assertEquals(expected, fadeProgress(positionMs, overlapMs), 0.0001f)
    }

    @Test
    fun `focus changes map to player actions`() {
        assertEquals(FocusChange.GAIN, focusChangeOf(AudioManager.AUDIOFOCUS_GAIN))
        assertEquals(FocusChange.LOSS, focusChangeOf(AudioManager.AUDIOFOCUS_LOSS))
        assertEquals(FocusChange.PAUSE, focusChangeOf(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
        assertEquals(FocusChange.DUCK, focusChangeOf(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK))
        assertNull(focusChangeOf(AudioManager.AUDIOFOCUS_NONE))
    }
}
