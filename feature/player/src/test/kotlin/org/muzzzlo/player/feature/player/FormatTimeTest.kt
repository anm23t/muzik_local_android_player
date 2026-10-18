package org.muzzzlo.player.feature.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class FormatTimeTest {
    @ParameterizedTest
    @CsvSource("0, 0:00", "999, 0:00", "1000, 0:01", "61000, 1:01", "3600000, 60:00", "-5, 0:00")
    fun `formats milliseconds as minutes and seconds`(
        ms: Long,
        expected: String,
    ) {
        assertEquals(expected, formatTime(ms))
    }
}
