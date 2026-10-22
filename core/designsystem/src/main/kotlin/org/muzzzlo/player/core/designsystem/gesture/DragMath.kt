package org.muzzzlo.player.core.designsystem.gesture

import kotlin.math.roundToInt

/** List index (queue, playlist) where a row ends up when it was dragged by [offsetPx] from [startIndex]. */
fun dragTargetIndex(
    startIndex: Int,
    offsetPx: Float,
    rowHeightPx: Float,
    size: Int,
): Int {
    if (size <= 0 || rowHeightPx <= 0f) return startIndex
    return (startIndex + (offsetPx / rowHeightPx).roundToInt()).coerceIn(0, size - 1)
}
