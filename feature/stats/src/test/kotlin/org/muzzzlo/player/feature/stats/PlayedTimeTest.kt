package org.muzzzlo.player.feature.stats

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayedTimeTest {
    @Test
    fun `time is split into hours and minutes`() {
        val cases =
            mapOf(
                -5L to PlayedTime(0, 0),
                0L to PlayedTime(0, 0),
                59_999L to PlayedTime(0, 0),
                60_000L to PlayedTime(0, 1),
                3_599_999L to PlayedTime(0, 59),
                3_600_000L to PlayedTime(1, 0),
                5_100_000L to PlayedTime(1, 25),
                90_000_000L to PlayedTime(25, 0),
            )
        cases.forEach { (ms, expected) -> assertEquals(expected, playedTimeOf(ms), "ms=$ms") }
    }
}
