package org.muzzzlo.player.feature.player

import kotlin.math.roundToInt

/** Queue index where a row ends up when it was dragged by [offsetPx] from [startIndex]. */
internal fun dragTargetIndex(
    startIndex: Int,
    offsetPx: Float,
    rowHeightPx: Float,
    size: Int,
): Int {
    if (size <= 0 || rowHeightPx <= 0f) return startIndex
    return (startIndex + (offsetPx / rowHeightPx).roundToInt()).coerceIn(0, size - 1)
}
