package org.muzzzlo.player.core.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ready-made curves of the equalizer (task 1.2.8), gains in dB for [Equalizer.FREQUENCIES]. [CUSTOM] is the user's
 * own curve ([EqualizerSettings.customDb]).
 */
@Suppress("MagicNumber") // The curves are data: one gain per band.
enum class EqualizerPreset(
    val gainsDb: List<Float>?,
) {
    FLAT(List(Equalizer.BAND_COUNT) { 0f }),
    BASS(listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f)),
    TREBLE(listOf(0f, 0f, 0f, 0f, 0f, 1f, 2f, 4f, 5f, 6f)),
    VOCAL(listOf(-2f, -2f, -1f, 1f, 3f, 4f, 3f, 1f, 0f, -1f)),
    ROCK(listOf(5f, 4f, 2f, -1f, -2f, -1f, 1f, 3f, 4f, 5f)),
    POP(listOf(-1f, 1f, 3f, 4f, 3f, 0f, -1f, -1f, 1f, 2f)),
    JAZZ(listOf(3f, 2f, 1f, 2f, -1f, -1f, 0f, 1f, 2f, 3f)),
    CLASSICAL(listOf(4f, 3f, 2f, 1f, -1f, -1f, 0f, 2f, 3f, 4f)),
    ELECTRONIC(listOf(5f, 4f, 1f, 0f, -2f, 1f, 0f, 1f, 4f, 5f)),
    CUSTOM(null),
}

/**
 * Equalizer settings (ARCHITECTURE.md, section 9). Decibel values are kept in steps of [Equalizer.STEP_DB].
 *
 * @property preset the curve in use; [EqualizerPreset.CUSTOM] plays [customDb].
 * @property customDb the user's own curve, one gain per band; kept while a ready-made curve is chosen.
 * @property preampDb added to every band, from [Equalizer.MIN_DB] to [Equalizer.MAX_DB].
 */
data class EqualizerSettings(
    val enabled: Boolean = false,
    val preset: EqualizerPreset = EqualizerPreset.FLAT,
    val customDb: List<Float> = List(Equalizer.BAND_COUNT) { 0f },
    val preampDb: Float = 0f,
) {
    /** Gains of the curve in use, one per band. */
    val bandsDb: List<Float> get() = preset.gainsDb ?: customDb

    /** The same settings with every value in range and step and exactly [Equalizer.BAND_COUNT] custom bands. */
    fun clamped(): EqualizerSettings =
        copy(
            customDb = List(Equalizer.BAND_COUNT) { Equalizer.clampDb(customDb.getOrElse(it) { 0f }) },
            preampDb = Equalizer.clampDb(preampDb),
        )

    /**
     * Band [index] moved by [steps] of [Equalizer.STEP_DB]. A ready-made curve becomes the starting point of the
     * user's own one, so the change never jumps from an old custom curve.
     */
    fun withBandStepped(
        index: Int,
        steps: Int,
    ): EqualizerSettings {
        val start = bandsDb.toMutableList()
        if (index !in start.indices) return this
        start[index] = Equalizer.clampDb(start[index] + steps * Equalizer.STEP_DB)
        return copy(preset = EqualizerPreset.CUSTOM, customDb = start)
    }

    /** Band [index] set to [gainDb] (rounded to the step), for a dragged slider; see [withBandStepped]. */
    fun withBand(
        index: Int,
        gainDb: Float,
    ): EqualizerSettings {
        val start = bandsDb.toMutableList()
        if (index !in start.indices) return this
        start[index] = Equalizer.clampDb(gainDb)
        return copy(preset = EqualizerPreset.CUSTOM, customDb = start)
    }

    /** True when the equalizer changes nothing: off, or a flat curve without preamp. */
    val isNeutral: Boolean get() = !enabled || (preampDb == 0f && bandsDb.all { it == 0f })
}

/**
 * One second-order filter section in the normalised form `y = b0 x + b1 x1 + b2 x2 - a1 y1 - a2 y2`.
 * [IDENTITY] passes the signal unchanged.
 */
data class Biquad(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a1: Double,
    val a2: Double,
) {
    /** Gain of the filter at [frequencyHz] for [sampleRate], in dB. */
    fun responseDb(
        frequencyHz: Double,
        sampleRate: Int,
    ): Double {
        val w = 2 * PI * frequencyHz / sampleRate
        val cos1 = cos(w)
        val sin1 = sin(w)
        val cos2 = cos(2 * w)
        val sin2 = sin(2 * w)
        // H(e^jw) = (b0 + b1 e^-jw + b2 e^-2jw) / (1 + a1 e^-jw + a2 e^-2jw)
        val numRe = b0 + b1 * cos1 + b2 * cos2
        val numIm = -(b1 * sin1 + b2 * sin2)
        val denRe = 1 + a1 * cos1 + a2 * cos2
        val denIm = -(a1 * sin1 + a2 * sin2)
        val power = (numRe * numRe + numIm * numIm) / (denRe * denRe + denIm * denIm)
        return DB_PER_POWER_DECADE * log10(power)
    }

    companion object {
        val IDENTITY = Biquad(1.0, 0.0, 0.0, 0.0, 0.0)
        private const val DB_PER_POWER_DECADE = 10.0
    }
}

/**
 * The 10-band equalizer (task 1.2.8, `docs/decisions/014-equalizer.md`): one peaking filter per band after the
 * Audio EQ Cookbook of R. Bristow-Johnson, a bandwidth of one octave, and a preamp. Pure Kotlin, so the frequency
 * response is unit-tested; `core:playback` runs the filters on the samples.
 */
object Equalizer {
    /** Centre frequencies of the bands, an octave apart. */
    val FREQUENCIES = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    val BAND_COUNT = FREQUENCIES.size

    const val MIN_DB = -12f
    const val MAX_DB = 12f
    const val STEP_DB = 0.5f

    /** Quality of a one-octave band: `sqrt(2)`. */
    val Q = sqrt(2.0)

    /** Above this share of the sample rate a band is left out: the filter would sit at the Nyquist frequency. */
    private const val MAX_FREQUENCY_SHARE = 0.45

    private const val DB_PER_AMPLITUDE_DECADE = 20.0
    private const val DB_PER_SHELF = 40.0
    private const val GRID_POINTS = 512
    private const val GRID_FROM_HZ = 20.0
    private const val GRID_TO_HZ = 20000.0

    /** [db] brought into the range and to the nearest step; NaN becomes 0. */
    fun clampDb(db: Float): Float {
        if (db.isNaN()) return 0f
        return ((db / STEP_DB).roundToInt() * STEP_DB).coerceIn(MIN_DB, MAX_DB)
    }

    /** The peaking filter for one band; a band with no gain or above the usable range passes the signal. */
    fun peaking(
        frequencyHz: Int,
        gainDb: Float,
        sampleRate: Int,
    ): Biquad {
        if (gainDb == 0f || frequencyHz >= sampleRate * MAX_FREQUENCY_SHARE) return Biquad.IDENTITY
        val a = 10.0.pow(gainDb / DB_PER_SHELF)
        val w0 = 2 * PI * frequencyHz / sampleRate
        val alpha = sin(w0) / (2 * Q)
        val cosW0 = cos(w0)
        val a0 = 1 + alpha / a
        return Biquad(
            b0 = (1 + alpha * a) / a0,
            b1 = -2 * cosW0 / a0,
            b2 = (1 - alpha * a) / a0,
            a1 = -2 * cosW0 / a0,
            a2 = (1 - alpha / a) / a0,
        )
    }

    /** One filter per band of [settings] (identity for bands without gain). */
    fun filters(
        settings: EqualizerSettings,
        sampleRate: Int,
    ): List<Biquad> = FREQUENCIES.mapIndexed { i, f -> peaking(f, settings.bandsDb[i], sampleRate) }

    /** Gain of the whole equalizer with its preamp at [frequencyHz], in dB; 0 when it is off. */
    fun responseDb(
        settings: EqualizerSettings,
        frequencyHz: Double,
        sampleRate: Int,
    ): Double {
        if (!settings.enabled) return 0.0
        return settings.preampDb + filters(settings, sampleRate).sumOf { it.responseDb(frequencyHz, sampleRate) }
    }

    /**
     * The highest gain of the equalizer between 20 Hz and 20 kHz (and the band centres), in dB, at least 0. With
     * clip protection the output is lowered by this much, so a full-scale sine at any frequency stays at full scale
     * at most; ReplayGain protects its own peak before.
     */
    fun headroomDb(
        settings: EqualizerSettings,
        sampleRate: Int,
    ): Double {
        if (settings.isNeutral) return 0.0
        val filters = filters(settings, sampleRate)
        val top = minOf(GRID_TO_HZ, sampleRate * MAX_FREQUENCY_SHARE)
        val grid = List(GRID_POINTS) { GRID_FROM_HZ * exp(ln(top / GRID_FROM_HZ) * it / (GRID_POINTS - 1)) }
        val points = grid + FREQUENCIES.map(Int::toDouble).filter { it <= top }
        val peak = points.maxOf { f -> settings.preampDb + filters.sumOf { it.responseDb(f, sampleRate) } }
        return maxOf(0.0, peak)
    }

    /** Linear factor applied after the filters: the preamp, lowered by the headroom when [clipProtection] is on. */
    fun outputFactor(
        settings: EqualizerSettings,
        sampleRate: Int,
        clipProtection: Boolean,
    ): Double {
        if (!settings.enabled) return 1.0
        val db = settings.preampDb - if (clipProtection) headroomDb(settings, sampleRate) else 0.0
        return 10.0.pow(db / DB_PER_AMPLITUDE_DECADE)
    }
}
