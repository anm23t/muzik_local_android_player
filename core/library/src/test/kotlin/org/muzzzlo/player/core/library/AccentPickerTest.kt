package org.muzzzlo.player.core.library

import com.materialkolor.contrast.Contrast
import com.materialkolor.hct.Hct
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class AccentPickerTest {
    private fun solid(
        argb: Long,
        count: Int = 400,
    ) = IntArray(count) { argb.toInt() }

    private fun mix(vararg parts: Pair<Long, Int>) =
        parts.flatMap { (argb, n) ->
            List(
                n,
            ) { argb.toInt() }
        }.toIntArray()

    private fun contrastOnSurface(accent: Int) =
        Contrast.ratioOfTones(
            Hct.fromInt(accent).tone,
            Hct.fromInt(0xFF121212.toInt()).tone,
        )

    private fun contrastWithWhite(accent: Int): Double = Contrast.ratioOfTones(100.0, Hct.fromInt(accent).tone)

    @Test
    fun `no pixels give no accent`() {
        assertEquals(AccentPicker.NONE, AccentPicker.pick(IntArray(0)))
    }

    @ParameterizedTest(name = "{0} gives no accent")
    @ValueSource(longs = [0xFF000000, 0xFFFFFFFF, 0xFF808080, 0xFF1A1A1A, 0xFFEEEEEE, 0xFF7F8083])
    fun `black, white and grey covers give no accent`(argb: Long) {
        assertEquals(AccentPicker.NONE, AccentPicker.pick(solid(argb)))
    }

    @Test
    fun `a transparent picture gives no accent`() {
        assertEquals(AccentPicker.NONE, AccentPicker.pick(solid(0x00FF0000)))
    }

    @Test
    fun `transparent pixels do not take part`() {
        val pixels = mix(0x00FF0000L to 1000, 0xFF2244CCL to 300)
        val hue = Hct.fromInt(AccentPicker.pick(pixels)).hue
        assertTrue(hue in 250.0..290.0, "blue expected, hue $hue")
    }

    @Test
    fun `the result is opaque`() {
        assertEquals(0xFF, AccentPicker.pick(solid(0xFFCD253E)) ushr 24)
    }

    @ParameterizedTest(name = "{0} meets both contrast rules")
    @ValueSource(
        longs = [
            0xFFCD253E, 0xFF0A1A8C, 0xFF000066, 0xFFFFEE55, 0xFFF7F060,
            0xFF00FF00, 0xFFFF00FF, 0xFF22AA66, 0xFFFF6A00,
        ],
    )
    fun `contrast is at least 3 to 1 on the surface and for white on the accent`(argb: Long) {
        val accent = AccentPicker.pick(solid(argb))
        assertTrue(accent != AccentPicker.NONE)
        assertTrue(contrastOnSurface(accent) >= 3.0, "surface ${contrastOnSurface(accent)}")
        assertTrue(contrastWithWhite(accent) >= 3.0, "white ${contrastWithWhite(accent)}")
    }

    @Test
    fun `a dark blue is lightened and keeps its hue`() {
        val source = Hct.fromInt(0xFF000066.toInt())
        val result = Hct.fromInt(AccentPicker.pick(solid(0xFF000066)))
        assertTrue(result.tone > source.tone)
        assertTrue(Math.abs(result.hue - source.hue) < 8.0, "hue ${source.hue} to ${result.hue}")
    }

    @Test
    fun `a light yellow is darkened and keeps its hue`() {
        val source = Hct.fromInt(0xFFFFEE55.toInt())
        val result = Hct.fromInt(AccentPicker.pick(solid(0xFFFFEE55)))
        assertTrue(result.tone < source.tone)
        assertTrue(Math.abs(result.hue - source.hue) < 8.0, "hue ${source.hue} to ${result.hue}")
    }

    @Test
    fun `an acid color has its chroma capped`() {
        val result = Hct.fromInt(AccentPicker.pick(solid(0xFF00FF00)))
        assertTrue(result.chroma <= AccentPicker.MAX_CHROMA + 1.0, "chroma ${result.chroma}")
    }

    @Test
    fun `a color that already fits stays almost as it is`() {
        val source = Hct.fromInt(0xFFCD253E.toInt())
        val result = Hct.fromInt(AccentPicker.pick(solid(0xFFCD253E)))
        assertTrue(Math.abs(result.hue - source.hue) < 3.0)
        assertTrue(Math.abs(result.tone - source.tone) < 3.0)
    }

    @Test
    fun `the dominant color wins over a small spot`() {
        val pixels = mix(0xFF1144DDL to 900, 0xFFDD2211L to 100)
        val hue = Hct.fromInt(AccentPicker.pick(pixels)).hue
        assertTrue(hue in 250.0..290.0, "blue expected, hue $hue")
    }

    @Test
    fun `grey around a colored subject gives the subject color`() {
        val pixels = mix(0xFF808080L to 800, 0xFF22AA44L to 200)
        val hue = Hct.fromInt(AccentPicker.pick(pixels)).hue
        assertTrue(hue in 120.0..160.0, "green expected, hue $hue")
    }

    @Test
    fun `the same pixels always give the same color`() {
        val pixels = mix(0xFF1144DDL to 500, 0xFFDD2211L to 300, 0xFF22AA44L to 200)
        assertEquals(AccentPicker.pick(pixels), AccentPicker.pick(pixels))
    }
}
