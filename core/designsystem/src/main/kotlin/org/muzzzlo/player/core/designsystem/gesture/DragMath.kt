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

/**
 * How far the row at [index] steps aside while the row from [fromIndex] is held over [targetIndex] (task 2.0.9):
 * the rows between the old and the new place move by one step towards the old place, showing where the held row
 * will land. Zero for the held row itself and for the others.
 */
fun neighborShift(
    index: Int,
    fromIndex: Int,
    targetIndex: Int,
    stepPx: Float,
): Float =
    when {
        fromIndex < 0 || index == fromIndex -> 0f
        targetIndex > fromIndex && index in (fromIndex + 1)..targetIndex -> -stepPx
        targetIndex < fromIndex && index in targetIndex until fromIndex -> stepPx
        else -> 0f
    }

/**
 * The scroll of one frame while a held row is near an edge of the list (task 2.0.9): negative scrolls up, positive
 * down, zero away from the edges. The closer the row is to the edge (or past it), the faster, up to [maxStepPx]
 * within the [edgePx] zone.
 */
fun autoScrollStep(
    rowTopPx: Float,
    rowBottomPx: Float,
    viewportStartPx: Float,
    viewportEndPx: Float,
    edgePx: Float,
    maxStepPx: Float,
): Float {
    if (edgePx <= 0f || viewportEndPx - viewportStartPx <= edgePx * 2) return 0f
    val intoTop = viewportStartPx + edgePx - rowTopPx
    val intoBottom = rowBottomPx - (viewportEndPx - edgePx)
    return when {
        intoTop > 0f -> -maxStepPx * (intoTop / edgePx).coerceAtMost(1f)
        intoBottom > 0f -> maxStepPx * (intoBottom / edgePx).coerceAtMost(1f)
        else -> 0f
    }
}

/**
 * Keys of a list where the same item may stand twice (a queue): the id with the number of its earlier repeats, so
 * a row keeps its key when other rows move, and animations follow it.
 */
fun <T> stableKeys(
    items: List<T>,
    id: (T) -> Any,
): List<String> {
    val seen = HashMap<Any, Int>()
    return items.map { item ->
        val key = id(item)
        val repeat = seen.merge(key, 1, Int::plus)!! - 1
        "$key#$repeat"
    }
}
