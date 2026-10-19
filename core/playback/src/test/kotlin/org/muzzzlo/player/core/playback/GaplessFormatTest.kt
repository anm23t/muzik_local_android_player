package org.muzzzlo.player.core.playback

import androidx.media3.common.Format
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class GaplessFormatTest {
    @ParameterizedTest
    @CsvSource(
        "true, 576, 1105, 576, 1105",
        "true, 0, 0, 0, 0",
        "false, 576, 1105, 0, 0",
        "false, 576, 0, 0, 0",
        "false, 0, 1105, 0, 0",
        "false, 0, 0, 0, 0",
    )
    fun `trim data is kept only with gapless on`(
        gapless: Boolean,
        delay: Int,
        padding: Int,
        expectedDelay: Int,
        expectedPadding: Int,
    ) {
        val format = Format.Builder().setEncoderDelay(delay).setEncoderPadding(padding).build()
        val result = format.forGapless(gapless)
        assertEquals(expectedDelay, result.encoderDelay)
        assertEquals(expectedPadding, result.encoderPadding)
    }

    @ParameterizedTest
    @CsvSource("true", "false")
    fun `a format without trim data is returned as is`(gapless: Boolean) {
        val format = Format.Builder().build()
        assertSame(format, format.forGapless(gapless))
    }
}
