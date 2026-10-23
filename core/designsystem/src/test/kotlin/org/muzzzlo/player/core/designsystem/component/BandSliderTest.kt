package org.muzzzlo.player.core.designsystem.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class BandSliderTest {
    @ParameterizedTest(name = "y {0} of 160 -> {1} dB")
    @CsvSource(
        "0, 12",
        "80, 0",
        "160, -12",
        "40, 6",
        "-30, 12",
        "500, -12",
    )
    fun `value at a place of the track`(
        y: Float,
        expected: Float,
    ) = assertEquals(expected, bandValueAt(y, 160f, -12f, 12f), 1e-4f)

    @Test
    fun `place of a value is the inverse`() {
        listOf(-12f, -3.5f, 0f, 7f, 12f).forEach {
            assertEquals(it, bandValueAt(bandYOf(it, 160f, -12f, 12f), 160f, -12f, 12f), 1e-4f)
        }
    }

    @Test
    fun `values out of range sit at the ends and an empty track gives the middle`() {
        assertEquals(0f, bandYOf(40f, 160f, -12f, 12f))
        assertEquals(160f, bandYOf(-40f, 160f, -12f, 12f))
        assertEquals(0f, bandValueAt(10f, 0f, -12f, 12f))
    }
}
