package org.muzzzlo.player.core.library

import com.materialkolor.hct.Hct
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score
import com.materialkolor.utils.ColorUtils

/**
 * Picks the colors of the live background from a cover (task 2.0.8): the main colors of the picture by the same
 * quantizer as the accent, muted in HCT, so that white text stays readable on them under the veil. A dark cover
 * (mostly black) gives a black background, a colorless one greys as light as the cover (owner's remark after the
 * phone check). Works on plain ARGB pixels, so the rules are tested on the JVM.
 */
internal object BackgroundPalette {
    /** The live background uses this many colors. */
    const val SIZE = 4

    /** The default red of the app (DESIGN.md, section 2), the seed when the cover gives nothing. */
    const val DEFAULT_RED = 0xFFCD253E.toInt()

    private const val OPAQUE = 0xFF000000.toInt()
    private const val NO_COLOR = 0
    private const val MIN_ALPHA = 128
    private const val ALPHA_SHIFT = 24
    private const val QUANTIZER_COLORS = 128

    /** Colors below this chroma read as grey; a cover of only such colors gets a palette from its first color. */
    internal const val MIN_CHROMA = 12.0

    /** The spots stay soft: more chroma glows too much on black. */
    internal const val MAX_CHROMA = 40.0

    /** Tones of the spots, dark enough for white text, light enough to be seen under the veil. */
    internal const val MIN_TONE = 30.0
    internal const val MAX_TONE = 45.0

    /** A cover darker than this on average (L*, 0 black to 100 white) gives a black background. */
    internal const val DARK_COVER_LSTAR = 18.0

    /** Tones of the greys of a colorless cover: a dark grey cover gets [MIN_GREY_TONE], a white one [MAX_GREY_TONE]. */
    internal const val MIN_GREY_TONE = 20.0
    internal const val MAX_GREY_TONE = 70.0
    private const val MAX_LSTAR = 100.0

    /** The palette of a black background: the spots are drawn in black. */
    val BLACK: List<Int> = List(SIZE) { OPAQUE }

    /** Neighbours of a seed: the same hue turned a little, so a one-color cover still has movement. */
    private val HUE_STEPS = doubleArrayOf(0.0, 25.0, -25.0, 50.0)
    private val TONE_STEPS = doubleArrayOf(0.0, -6.0, 6.0, -3.0)
    private const val FULL_TURN = 360.0

    /** [SIZE] muted colors of [pixels]; the palette of [DEFAULT_RED] when there are no visible pixels. */
    fun pick(pixels: IntArray): List<Int> {
        val visible = pixels.filter { (it ushr ALPHA_SHIFT) >= MIN_ALPHA }.map { it or OPAQUE }.toIntArray()
        if (visible.isEmpty()) return fromSeed(DEFAULT_RED)
        val lightness = visible.sumOf { ColorUtils.lstarFromArgb(it) } / visible.size
        if (lightness < DARK_COVER_LSTAR) return BLACK
        val quantized = QuantizerCelebi.quantize(visible, QUANTIZER_COLORS)
        val colored =
            // Score answers its fallback when nothing is colored; 0 is never a quantized (opaque) color.
            Score.score(quantized, SIZE, NO_COLOR, true)
                .filter { it != NO_COLOR }
                .map(Hct::fromInt)
                .filter { it.chroma >= MIN_CHROMA }
                .distinctBy { it.toInt() }
        return when (colored.size) {
            0 -> greys(quantized.keys.first(), lightness)
            1 -> fromSeed(colored.first().toInt())
            else -> List(SIZE) { muted(colored[it % colored.size], TONE_STEPS[it]).toInt() }
        }
    }

    /** [SIZE] muted colors around [seed]: its hue and close neighbours. */
    fun fromSeed(seed: Int): List<Int> {
        val hct = Hct.fromInt(seed)
        return List(SIZE) { i ->
            val hue = (hct.hue + HUE_STEPS[i] + FULL_TURN) % FULL_TURN
            muted(Hct.from(hue, hct.chroma, hct.tone), TONE_STEPS[i]).toInt()
        }
    }

    /** A grey cover moves in greys of its own tint, as light as the cover: a white cover gives a light grey. */
    private fun greys(
        color: Int,
        coverLightness: Double,
    ): List<Int> {
        val hct = Hct.fromInt(color)
        val share = ((coverLightness - DARK_COVER_LSTAR) / (MAX_LSTAR - DARK_COVER_LSTAR)).coerceIn(0.0, 1.0)
        val tone = MIN_GREY_TONE + (MAX_GREY_TONE - MIN_GREY_TONE) * share
        return List(SIZE) { Hct.from(hct.hue, hct.chroma, (tone + TONE_STEPS[it]).coerceIn(0.0, MAX_LSTAR)).toInt() }
    }

    private fun muted(
        color: Hct,
        toneStep: Double,
    ): Hct =
        Hct.from(
            color.hue,
            color.chroma.coerceAtMost(MAX_CHROMA),
            (color.tone + toneStep).coerceIn(MIN_TONE, MAX_TONE),
        )
}
