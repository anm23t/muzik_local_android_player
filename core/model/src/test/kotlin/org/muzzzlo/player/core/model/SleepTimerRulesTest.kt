package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SleepTimerRulesTest {
    private val start = 1_000_000L
    private val thirty = SleepTimerRules.after(30, start)

    @Test
    fun `a timer of N minutes ends N minutes later`() = assertEquals(start + 30 * 60_000, thirty.endAtMs)

    @Test
    fun `a timer without length is refused`() {
        assertThrows<IllegalArgumentException> { SleepTimerRules.after(0, start) }
    }

    @Test
    fun `minutes count down on the clock, not on the playback`() {
        assertEquals(30 * 60_000L, SleepTimerRules.remainingMs(thirty, start, 0, null))
        assertEquals(60_000L, SleepTimerRules.remainingMs(thirty, thirty.endAtMs - 60_000, 123_456, 200_000))
        assertEquals(0L, SleepTimerRules.remainingMs(thirty, thirty.endAtMs + 5_000, 0, null))
    }

    @ParameterizedTest(name = "{0} ms before the end -> volume {1}, pause {2}")
    @CsvSource(
        "600000, 1, false",
        "30000, 1, false",
        "15000, 0.25, false",
        "3000, 0.01, false",
        "0, 0, true",
        "-5000, 0, true",
    )
    fun `minutes mode fades the last 30 seconds and then pauses`(
        beforeEndMs: Long,
        volume: Float,
        pause: Boolean,
    ) {
        val step = SleepTimerRules.step(thirty, thirty.endAtMs - beforeEndMs, 0, null)

        assertEquals(volume, step.volume, 1e-4f)
        assertEquals(pause, step.pause)
    }

    @Test
    fun `end of track counts to the end or to the crossfade start`() {
        assertEquals(50_000L, SleepTimerRules.remainingMs(SleepTimerMode.EndOfTrack, start, 150_000, 200_000))
        assertEquals(0L, SleepTimerRules.remainingMs(SleepTimerMode.EndOfTrack, start, 210_000, 200_000))
    }

    @Test
    fun `end of track with an unknown length waits at full volume`() {
        assertNull(SleepTimerRules.remainingMs(SleepTimerMode.EndOfTrack, start, 5_000, null))
        assertNull(SleepTimerRules.remainingMs(SleepTimerMode.EndOfTrack, start, 5_000, 0))
        assertEquals(
            SleepTimerStep(1f, pause = false),
            SleepTimerRules.step(SleepTimerMode.EndOfTrack, start, 5_000, null),
        )
    }

    @Test
    fun `end of track fades the last 10 seconds and leaves the pause to the end of the track`() {
        val early = SleepTimerRules.step(SleepTimerMode.EndOfTrack, start, 180_000, 200_000)
        val fading = SleepTimerRules.step(SleepTimerMode.EndOfTrack, start, 195_000, 200_000)
        val atEnd = SleepTimerRules.step(SleepTimerMode.EndOfTrack, start, 200_000, 200_000)

        assertEquals(1f, early.volume)
        assertEquals(0.25f, fading.volume, 1e-4f)
        assertEquals(SleepTimerStep(0f, pause = false), atEnd)
    }

    @Test
    fun `only an automatic move on in end of track mode pauses`() {
        assertTrue(SleepTimerRules.pausesOnTransition(SleepTimerMode.EndOfTrack, automatic = true))
        assertFalse(SleepTimerRules.pausesOnTransition(SleepTimerMode.EndOfTrack, automatic = false))
        assertFalse(SleepTimerRules.pausesOnTransition(thirty, automatic = true))
        assertFalse(SleepTimerRules.pausesOnTransition(null, automatic = true))
    }

    @Test
    fun `a fade shorter than the remaining time or of no length keeps full volume`() {
        assertEquals(1f, SleepTimerRules.volumeFor(40_000, 30_000))
        assertEquals(1f, SleepTimerRules.volumeFor(0, 0))
    }

    @Test
    fun `volume only falls while the end comes closer`() {
        val volumes = (30_000L downTo 0L step 500).map { SleepTimerRules.volumeFor(it, 30_000) }

        assertEquals(volumes.sortedDescending(), volumes)
    }
}
