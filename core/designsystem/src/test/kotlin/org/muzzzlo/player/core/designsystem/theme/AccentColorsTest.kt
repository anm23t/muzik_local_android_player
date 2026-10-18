package org.muzzzlo.player.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.math.abs

class AccentColorsTest {
    @Test
    fun `default red gives the mockup container within two steps per channel`() {
        val container = AccentColors.from(DefaultAccent).container
        assertChannelsClose(Color(0xFF241015), container)
    }

    @Test
    fun `shadow is the accent at 20 percent`() {
        val shadow = AccentColors.from(DefaultAccent).shadow
        assertEquals(0.2f, shadow.alpha, 0.01f)
        assertEquals(DefaultAccent.copy(alpha = 1f), shadow.copy(alpha = 1f))
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        // name, accent argb, expected hue
        "red default, FFCD253E, 351",
        "pure red, FFFF0000, 0",
        "pure green, FF00FF00, 120",
        "pure blue, FF0000FF, 240",
        "orange, FFFF8000, 30",
        "magenta, FFFF00FF, 300",
    )
    fun `container keeps the hue and is dark`(
        name: String,
        argb: String,
        expectedHue: Float,
    ) {
        val accent = Color(argb.toLong(16))
        val container = containerToneOf(accent).toHsl()
        val hueDistance = abs(expectedHue - container.hue).let { minOf(it, 360f - it) }
        assertTrue(hueDistance <= 2f, "$name: hue ${container.hue}")
        assertEquals(0.102f, container.lightness, 0.01f, name)
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        "black, FF000000",
        "white, FFFFFFFF",
        "gray, FF808080",
    )
    fun `achromatic accent gives an achromatic container`(
        name: String,
        argb: String,
    ) {
        val container = containerToneOf(Color(argb.toLong(16))).toHsl()
        assertEquals(0f, container.saturation, 0.001f, name)
    }

    @Test
    fun `hsl of the default red`() {
        val hsl = DefaultAccent.toHsl()
        assertEquals(351f, hsl.hue, 1f)
        assertEquals(0.694f, hsl.saturation, 0.01f)
        assertEquals(0.475f, hsl.lightness, 0.01f)
    }

    private fun assertChannelsClose(
        expected: Color,
        actual: Color,
    ) {
        val e = expected.toArgb()
        val a = actual.toArgb()
        listOf(16, 8, 0).forEach { shift ->
            val diff = abs((e shr shift and 0xFF) - (a shr shift and 0xFF))
            assertTrue(diff <= 2, "channel at $shift differs by $diff: ${Integer.toHexString(a)}")
        }
    }
}
