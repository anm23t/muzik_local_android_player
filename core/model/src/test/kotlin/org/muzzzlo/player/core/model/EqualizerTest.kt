package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import kotlin.math.abs

class EqualizerTest {
    private val rate = 44_100

    private fun custom(
        vararg bands: Pair<Int, Float>,
        preamp: Float = 0f,
    ): EqualizerSettings {
        val gains = MutableList(Equalizer.BAND_COUNT) { 0f }
        bands.forEach { (i, db) -> gains[i] = db }
        return EqualizerSettings(enabled = true, preset = EqualizerPreset.CUSTOM, customDb = gains, preampDb = preamp)
    }

    @ParameterizedTest(name = "{0} Hz, {1} dB at {2} Hz")
    @CsvSource(
        "1000, 6, 44100",
        "1000, -12, 44100",
        "31, 12, 48000",
        "16000, 6, 48000",
        "250, 0.5, 96000",
    )
    fun `a band has its gain at its centre`(
        frequency: Int,
        gain: Float,
        sampleRate: Int,
    ) = assertEquals(
        gain.toDouble(),
        Equalizer.peaking(frequency, gain, sampleRate).responseDb(frequency.toDouble(), sampleRate),
        0.01,
    )

    @Test
    fun `a band leaves frequencies far from it alone`() {
        val filter = Equalizer.peaking(1000, 12f, rate)

        assertEquals(0.0, filter.responseDb(20.0, rate), 0.1)
        assertEquals(0.0, filter.responseDb(18_000.0, rate), 0.2)
    }

    @Test
    fun `one octave away the gain is roughly a third`() {
        val db = Equalizer.peaking(1000, 12f, rate).responseDb(2000.0, rate)

        assertTrue(db in 2.0..6.0, "$db dB")
    }

    @Test
    fun `cut and boost of the same size are mirror images`() {
        val boost = Equalizer.peaking(500, 9f, rate)
        val cut = Equalizer.peaking(500, -9f, rate)

        listOf(100.0, 400.0, 500.0, 700.0, 3000.0).forEach {
            assertEquals(-boost.responseDb(it, rate), cut.responseDb(it, rate), 1e-6, "$it Hz")
        }
    }

    @Test
    fun `no gain and bands above the usable range pass the signal`() {
        assertEquals(Biquad.IDENTITY, Equalizer.peaking(1000, 0f, rate))
        assertEquals(Biquad.IDENTITY, Equalizer.peaking(16000, 6f, 22_050))
        assertEquals(0.0, Biquad.IDENTITY.responseDb(1234.0, rate), 1e-12)
    }

    @Test
    fun `flat curve without preamp is neutral and exactly unity`() {
        val flat = EqualizerSettings(enabled = true)

        assertTrue(flat.isNeutral)
        assertTrue(Equalizer.filters(flat, rate).all { it == Biquad.IDENTITY })
        assertEquals(1.0, Equalizer.outputFactor(flat, rate, clipProtection = true))
        assertEquals(0.0, Equalizer.headroomDb(flat, rate))
    }

    @Test
    fun `off is neutral whatever the curve`() {
        val off = custom(3 to 12f, preamp = 6f).copy(enabled = false)

        assertTrue(off.isNeutral)
        assertEquals(0.0, Equalizer.responseDb(off, 250.0, rate))
        assertEquals(1.0, Equalizer.outputFactor(off, rate, clipProtection = false))
    }

    @Test
    fun `the whole response adds the bands and the preamp`() {
        val settings = custom(5 to 6f, preamp = -3f)

        assertEquals(3.0, Equalizer.responseDb(settings, 1000.0, rate), 0.05)
        assertEquals(-3.0, Equalizer.responseDb(settings, 30.0, rate), 0.05)
    }

    @Test
    fun `headroom is the highest point of the curve`() {
        assertEquals(6.0, Equalizer.headroomDb(custom(5 to 6f), rate), 0.05)
        // Neighbouring boosts overlap, so the peak is higher than either band.
        assertTrue(Equalizer.headroomDb(custom(4 to 6f, 5 to 6f), rate) > 6.5)
        assertEquals(0.0, Equalizer.headroomDb(custom(5 to -6f), rate))
        assertEquals(0.0, Equalizer.headroomDb(custom(5 to 6f, preamp = -12f), rate))
    }

    @Test
    fun `clip protection keeps the loudest frequency at full scale`() {
        val settings = custom(0 to 12f, 1 to 12f, preamp = 3f)
        val factor = Equalizer.outputFactor(settings, rate, clipProtection = true)
        val factorDb = 20 * kotlin.math.log10(factor)
        val loudest = Equalizer.headroomDb(settings, rate)

        // preamp - headroom + (bands at the peak) = preamp - (preamp + bands) + bands = 0
        assertEquals(0.0, factorDb + loudest - settings.preampDb, 1e-9)
        assertTrue(factor < 1.0)
    }

    @Test
    fun `without clip protection only the preamp scales`() =
        assertEquals(0.5012, Equalizer.outputFactor(custom(5 to 12f, preamp = -6f), rate, clipProtection = false), 1e-4)

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "0.24, 0.0",
        "0.26, 0.5",
        "-0.74, -0.5",
        "12.3, 12",
        "-40, -12",
        "NaN, 0",
    )
    fun `decibels are stepped and clamped`(
        db: Float,
        expected: Float,
    ) = assertEquals(expected, Equalizer.clampDb(db))

    @ParameterizedTest
    @EnumSource(EqualizerPreset::class)
    fun `presets have one gain per band within range`(preset: EqualizerPreset) {
        val gains = preset.gainsDb ?: return
        assertEquals(Equalizer.BAND_COUNT, gains.size)
        assertTrue(gains.all { abs(it) <= Equalizer.MAX_DB })
    }

    @Test
    fun `a step on a ready-made curve starts the custom curve from it`() {
        val rock = EqualizerSettings(enabled = true, preset = EqualizerPreset.ROCK, customDb = List(10) { -9f })

        val changed = rock.withBandStepped(0, 2)

        assertEquals(EqualizerPreset.CUSTOM, changed.preset)
        assertEquals(listOf(6f) + EqualizerPreset.ROCK.gainsDb!!.drop(1), changed.customDb)
    }

    @Test
    fun `band changes stop at the range and ignore unknown bands`() {
        val settings = custom(2 to 11.5f)

        assertEquals(12f, settings.withBandStepped(2, 5).customDb[2])
        assertEquals(-12f, settings.withBand(2, -99f).customDb[2])
        assertEquals(3.5f, settings.withBand(2, 3.4f).customDb[2])
        assertEquals(settings, settings.withBandStepped(10, 1))
        assertEquals(settings, settings.withBand(-1, 1f))
    }

    @Test
    fun `choosing a preset keeps the custom curve for later`() {
        val mine = custom(1 to 4f)

        val jazz = mine.copy(preset = EqualizerPreset.JAZZ)

        assertEquals(EqualizerPreset.JAZZ.gainsDb, jazz.bandsDb)
        assertEquals(mine.customDb, jazz.copy(preset = EqualizerPreset.CUSTOM).bandsDb)
        assertFalse(jazz.isNeutral)
    }

    @Test
    fun `clamped repairs a stored curve of the wrong length and bad values`() {
        val broken = EqualizerSettings(customDb = listOf(30f, 0.3f), preampDb = Float.NaN)

        assertEquals(listOf(12f, 0.5f) + List(8) { 0f }, broken.clamped().customDb)
        assertEquals(0f, broken.clamped().preampDb)
        assertEquals(EqualizerSettings(), EqualizerSettings(customDb = List(12) { 0f }).clamped())
    }
}
