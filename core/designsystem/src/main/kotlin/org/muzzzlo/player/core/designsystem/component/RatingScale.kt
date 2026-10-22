package org.muzzzlo.player.core.designsystem.component

import java.util.Locale
import kotlin.math.roundToInt

/**
 * The rating scale 0.0-10.0 in steps of 0.1, kept as integer tenths 0..100 (`docs/decisions/008-rating-tenths.md`).
 * Pure functions for the rating dialog.
 */
object RatingScale {
    const val MIN = 0
    const val MAX = 100
    private const val TENTHS_PER_POINT = 10

    /** The rating the dialog starts from for a track that has none. */
    const val DEFAULT = 50

    /** The tenths for a position on the bar, [share] in [0, 1]; values outside are cut to the ends. */
    fun fromShare(share: Float): Int = (share.coerceIn(0f, 1f) * MAX).roundToInt()

    /** The position on the bar for [tenths], in [0, 1]. */
    fun toShare(tenths: Int): Float = tenths.coerceIn(MIN, MAX) / MAX.toFloat()

    /** [tenths] moved by [steps] tenths and kept inside the scale. */
    fun step(
        tenths: Int,
        steps: Int,
    ): Int = (tenths + steps).coerceIn(MIN, MAX)

    /** "6.7" for 67, "10.0" for 100, with a point in every language. */
    fun format(tenths: Int): String {
        val value = tenths.coerceIn(MIN, MAX)
        return String.format(Locale.ROOT, "%d.%d", value / TENTHS_PER_POINT, value % TENTHS_PER_POINT)
    }
}
