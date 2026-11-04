package org.muzzzlo.player.core.library

import com.materialkolor.hct.Hct
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.min

class BackgroundPaletteTest {
    private fun mix(vararg parts: Pair<Long, Int>) =
        parts.flatMap { (argb, n) -> List(n) { argb.toInt() } }.toIntArray()

    private fun hueDistance(
        a: Double,
        b: Double,
    ): Double = min(abs(a - b), 360 - abs(a - b))

    /** Rounding to RGB moves tone and chroma a little. */
    private fun assertMuted(palette: List<Int>) {
        assertEquals(BackgroundPalette.SIZE, palette.size)
        palette.map(Hct::fromInt).forEach {
            assertTrue(it.tone in BackgroundPalette.MIN_TONE - 1..BackgroundPalette.MAX_TONE + 1, "tone ${it.tone}")
            assertTrue(it.chroma <= BackgroundPalette.MAX_CHROMA + 1, "chroma ${it.chroma}")
        }
    }

    @Test
    fun `a two-colour cover gives both hues, muted`() {
        val palette = BackgroundPalette.pick(mix(0xFF1E88E5 to 600, 0xFFFDD835 to 400))
        assertMuted(palette)
        val hues = palette.map { Hct.fromInt(it).hue }
        val blue = Hct.fromInt(0xFF1E88E5.toInt()).hue
        val yellow = Hct.fromInt(0xFFFDD835.toInt()).hue
        assertTrue(hues.any { hueDistance(it, blue) < 10 }, "blue in $hues")
        assertTrue(hues.any { hueDistance(it, yellow) < 15 }, "yellow in $hues")
    }

    @Test
    fun `a one-colour cover moves in neighbours of its hue`() {
        val palette = BackgroundPalette.pick(IntArray(500) { 0xFF2E7D32.toInt() })
        assertMuted(palette)
        val green = Hct.fromInt(0xFF2E7D32.toInt()).hue
        palette.forEach { assertTrue(hueDistance(Hct.fromInt(it).hue, green) <= 55) }
        assertEquals(BackgroundPalette.SIZE, palette.distinct().size)
    }

    @Test
    fun `an acid cover is calmed down`() {
        assertMuted(BackgroundPalette.pick(IntArray(500) { 0xFFFF00FF.toInt() }))
        assertMuted(BackgroundPalette.pick(IntArray(500) { 0xFF00FF00.toInt() }))
    }

    @Test
    fun `a grey cover gives greys`() {
        val palette = BackgroundPalette.pick(mix(0xFF000000 to 300, 0xFFFFFFFF to 300, 0xFF808080 to 300))
        assertEquals(BackgroundPalette.SIZE, palette.size)
        palette.forEach { assertTrue(Hct.fromInt(it).chroma < BackgroundPalette.MIN_CHROMA, "chroma of $it") }
    }

    @Test
    fun `no visible pixels give the palette of the default red`() {
        val expected = BackgroundPalette.fromSeed(BackgroundPalette.DEFAULT_RED)
        assertEquals(expected, BackgroundPalette.pick(IntArray(0)))
        assertEquals(expected, BackgroundPalette.pick(IntArray(100) { 0x10FF0000 }))
    }

    @Test
    fun `the default palette is a muted red`() {
        val palette = BackgroundPalette.fromSeed(BackgroundPalette.DEFAULT_RED)
        assertMuted(palette)
        val red = Hct.fromInt(BackgroundPalette.DEFAULT_RED).hue
        assertTrue(hueDistance(Hct.fromInt(palette.first()).hue, red) < 5)
    }
}
