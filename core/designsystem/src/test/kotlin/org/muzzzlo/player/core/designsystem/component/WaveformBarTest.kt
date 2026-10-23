package org.muzzzlo.player.core.designsystem.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class WaveformBarTest {
    @ParameterizedTest(name = "width {0}, bar {1}, gap {2} -> {3}")
    @CsvSource(
        "100, 3, 2, 20",
        "98, 3, 2, 20",
        "97, 3, 2, 19",
        "3, 3, 2, 1",
        "2, 3, 2, 0",
        "0, 3, 2, 0",
        "100, 0, 2, 0",
    )
    fun `bar count`(
        width: Float,
        bar: Float,
        gap: Float,
        expected: Int,
    ) = assertEquals(expected, waveformBarCount(width, bar, gap))
}
