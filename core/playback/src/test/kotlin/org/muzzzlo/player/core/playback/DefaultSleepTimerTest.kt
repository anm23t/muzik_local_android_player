package org.muzzzlo.player.core.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.model.SleepTimerMode
import org.muzzzlo.player.core.model.SleepTimerRules

class DefaultSleepTimerTest {
    private var now = 5_000L
    private val timer = DefaultSleepTimer { now }

    @Test
    fun `no timer runs at first`() = assertNull(timer.mode.value)

    @Test
    fun `minutes start from the clock and count down on it`() {
        timer.start(15)
        now += 14 * 60_000

        val mode = timer.mode.value as SleepTimerMode.After
        assertEquals(5_000L + 15 * 60_000, mode.endAtMs)
        assertEquals(60_000L, SleepTimerRules.remainingMs(mode, timer.nowMs(), 0, null))
    }

    @Test
    fun `a new timer replaces the running one and cancel clears it`() {
        timer.start(60)
        timer.startEndOfTrack()
        assertEquals(SleepTimerMode.EndOfTrack, timer.mode.value)

        timer.start(30)
        assertEquals(5_000L + 30 * 60_000, (timer.mode.value as SleepTimerMode.After).endAtMs)

        timer.cancel()
        assertNull(timer.mode.value)
    }
}
