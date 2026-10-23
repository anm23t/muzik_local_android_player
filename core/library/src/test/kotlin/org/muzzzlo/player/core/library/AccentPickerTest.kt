package org.muzzzlo.player.core.library

import com.materialkolor.contrast.Contrast
import com.materialkolor.hct.Hct
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
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

    @ParameterizedTest(name = "{0} gives a neutral grey")
    @ValueSource(
        longs = [
            0xFF000000, 0xFFFFFFFF, 0xFF808080, 0xFF1A1A1A, 0xFFEEEEEE, 0xFF7F8083,
            // Sepia and a faded blue: some chroma, but below MIN_CHROMA.
            0xFF8C7B6A, 0xFF6E7480,
        ],
    )
    fun `colorless covers give a neutral grey`(argb: Long) {
        val accent = AccentPicker.pick(solid(argb))
        assertTrue(accent != AccentPicker.NONE)
        val red = accent shr 16 and 0xFF
        assertTrue(red == accent shr 8 and 0xFF && red == accent and 0xFF, Integer.toHexString(accent))
    }

    @ParameterizedTest(name = "{0}: cover {1}, accent tone {2}")
    @CsvSource(
        "black, FF000000, 90",
        "white, FFFFFFFF, 60",
        "middle grey, FF777777, 75",
    )
    fun `the darker the colorless cover the lighter the grey`(
        name: String,
        argb: String,
        expectedTone: Double,
    ) {
        val tone = Hct.fromInt(AccentPicker.pick(solid(argb.toLong(16)))).tone
        assertEquals(expectedTone, tone, 1.5, name)
    }

    @Test
    fun `a black and white picture gives a grey by its average tone`() {
        val mostlyBlack = AccentPicker.pick(mix(0xFF000000L to 900, 0xFFFFFFFFL to 100))
        val mostlyWhite = AccentPicker.pick(mix(0xFF000000L to 100, 0xFFFFFFFFL to 900))
        assertTrue(Hct.fromInt(mostlyBlack).tone > Hct.fromInt(mostlyWhite).tone)
    }

    @ParameterizedTest(name = "{0} stays above 3 to 1 on the surface")
    @ValueSource(longs = [0xFF000000, 0xFFFFFFFF, 0xFF808080])
    fun `a neutral grey keeps the contrast on the surface`(argb: Long) {
        val accent = AccentPicker.pick(solid(argb))
        assertTrue(contrastOnSurface(accent) >= 3.0, "surface ${contrastOnSurface(accent)}")
    }

    @Test
    fun `a dull dominant color gives way to a colored one`() {
        val pixels = mix(0xFF8C7B6AL to 800, 0xFF2244CCL to 200)
        val hue = Hct.fromInt(AccentPicker.pick(pixels)).hue
        assertTrue(hue in 250.0..290.0, "blue expected, hue $hue")
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
