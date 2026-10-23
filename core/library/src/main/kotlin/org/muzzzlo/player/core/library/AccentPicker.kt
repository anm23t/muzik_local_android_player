package org.muzzzlo.player.core.library

import com.materialkolor.contrast.Contrast
import com.materialkolor.hct.Hct
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score
import com.materialkolor.utils.ColorUtils

/**
 * Picks the accent from the pixels of a cover (DESIGN.md, section 4; docs/decisions/010-accent-extraction.md).
 * Works on plain ARGB pixels, so every rule is tested on the JVM without Android.
 */
internal object AccentPicker {
    /** Result of [pick] for a picture with no visible pixels: the default red is used. */
    const val NONE = 0

    private const val OPAQUE = 0xFF000000.toInt()
    private const val MIN_ALPHA = 128
    private const val QUANTIZER_COLORS = 128

    /** How many candidates [Score] returns; the first colored enough of them wins. */
    private const val CANDIDATES = 4

    /**
     * Chroma below this reads as grey on a black screen (sepia, faded prints): such a cover counts as colorless and
     * gets a neutral accent instead of a dull tint.
     */
    internal const val MIN_CHROMA = 16.0

    /** Tone of the neutral accent for a black cover: almost white. */
    internal const val NEUTRAL_TONE_DARK_COVER = 90.0

    /** Tone of the neutral accent for a white cover: a middle grey, still 3:1 against `#121212`. */
    internal const val NEUTRAL_TONE_LIGHT_COVER = 60.0
    private const val MAX_TONE = 100.0

    /** Chroma above this gives acid colors on a black screen; the cap keeps the hue and lowers the intensity. */
    internal const val MAX_CHROMA = 75.0

    /** `surface-elevated`, the lightest background an accent sits on (DESIGN.md, section 2). */
    private const val SURFACE_ARGB = 0xFF121212.toInt()
    private const val WHITE_TONE = 100.0

    /** Both contrast rules ask for 3:1; a little extra covers the rounding of the final RGB. */
    private const val MIN_CONTRAST = 3.05

    /**
     * The accent for [pixels] (ARGB), or [NONE] when there are no visible pixels. A colored cover gives an opaque color
     * that keeps the hue of the dominant color, has its chroma capped and its tone moved into the range where the
     * contrast is at least 3:1 both against `#121212` and for white text or an icon on top of it. A colorless cover
     * (black and white, grey, sepia) gives a neutral grey: the darker the cover, the lighter the grey.
     */
    fun pick(pixels: IntArray): Int {
        val visible = pixels.filter { (it ushr ALPHA_SHIFT) >= MIN_ALPHA }.map { it or OPAQUE }.toIntArray()
        if (visible.isEmpty()) return NONE
        // Score drops colors with almost no chroma (greys, black, white); the rest is checked against MIN_CHROMA.
        val dominant =
            Score.score(QuantizerCelebi.quantize(visible, QUANTIZER_COLORS), CANDIDATES, NONE, true)
                .filter { it != NONE }
                .map(Hct::fromInt)
                .firstOrNull { it.chroma >= MIN_CHROMA }
        return dominant?.let { fitted(it).toInt() } ?: neutralFor(visible)
    }

    /** A grey whose tone goes from [NEUTRAL_TONE_DARK_COVER] to [NEUTRAL_TONE_LIGHT_COVER] with the cover tone. */
    private fun neutralFor(pixels: IntArray): Int {
        val coverTone = pixels.sumOf { ColorUtils.lstarFromArgb(it) } / pixels.size
        val share = (coverTone / MAX_TONE).coerceIn(0.0, 1.0)
        val tone = NEUTRAL_TONE_DARK_COVER + (NEUTRAL_TONE_LIGHT_COVER - NEUTRAL_TONE_DARK_COVER) * share
        return ColorUtils.argbFromLstar(tone)
    }

    private fun fitted(color: Hct): Hct {
        val surfaceTone = Hct.fromInt(SURFACE_ARGB).tone
        val lowest = Contrast.lighter(surfaceTone, MIN_CONTRAST)
        val highest = Contrast.darker(WHITE_TONE, MIN_CONTRAST)
        val tone = color.tone.coerceIn(lowest, highest)
        return Hct.from(color.hue, color.chroma.coerceAtMost(MAX_CHROMA), tone)
    }

    private const val ALPHA_SHIFT = 24
}
