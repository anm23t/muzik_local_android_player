package org.muzzzlo.player.core.library

import com.materialkolor.contrast.Contrast
import com.materialkolor.hct.Hct
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score

/**
 * Picks the accent from the pixels of a cover (DESIGN.md, section 4; docs/decisions/010-accent-extraction.md).
 * Works on plain ARGB pixels, so every rule is tested on the JVM without Android.
 */
internal object AccentPicker {
    /** Result of [pick] when the cover gives no suitable color: the default red is used. */
    const val NONE = 0

    private const val OPAQUE = 0xFF000000.toInt()
    private const val MIN_ALPHA = 128
    private const val QUANTIZER_COLORS = 128

    /** Chroma above this gives acid colors on a black screen; the cap keeps the hue and lowers the intensity. */
    internal const val MAX_CHROMA = 75.0

    /** `surface-elevated`, the lightest background an accent sits on (DESIGN.md, section 2). */
    private const val SURFACE_ARGB = 0xFF121212.toInt()
    private const val WHITE_TONE = 100.0

    /** Both contrast rules ask for 3:1; a little extra covers the rounding of the final RGB. */
    private const val MIN_CONTRAST = 3.05

    /**
     * The accent for [pixels] (ARGB), or [NONE] when the cover has no color worth showing: no pixels, a grey, near
     * black or near white picture. The result is opaque, keeps the hue of the dominant color, has its chroma capped
     * and its tone moved into the range where the contrast is at least 3:1 both against `#121212` and for white
     * text or an icon on top of it.
     */
    fun pick(pixels: IntArray): Int {
        val visible = pixels.filter { (it ushr ALPHA_SHIFT) >= MIN_ALPHA }.map { it or OPAQUE }
        if (visible.isEmpty()) return NONE
        // Score drops colors with too little chroma (greys, black, white) and returns NONE when nothing is left.
        val dominant =
            Score.score(QuantizerCelebi.quantize(visible.toIntArray(), QUANTIZER_COLORS), 1, NONE, true).firstOrNull()
        if (dominant == null || dominant == NONE) return NONE
        return fitted(Hct.fromInt(dominant)).toInt()
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
