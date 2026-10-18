package org.muzzzlo.player.feature.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ProgressTest {
    @ParameterizedTest(name = "{0} of {1} = {2}")
    @CsvSource(
        "0, 1000, 0.0",
        "500, 1000, 0.5",
        "1000, 1000, 1.0",
        // The position can run past a duration that was estimated too short.
        "1500, 1000, 1.0",
        "-10, 1000, 0.0",
        // Unknown duration: nothing to show yet.
        "500, 0, 0.0",
        "500, -1, 0.0",
    )
    fun `progress is the played share clamped to 0 and 1`(
        positionMs: Long,
        durationMs: Long,
        expected: Float,
    ) {
        assertEquals(expected, progressOf(positionMs, durationMs))
    }
}
