package org.muzzzlo.player.core.designsystem.gesture

import kotlin.math.abs

/** Share of the width a drag must cover to switch the track. */
const val SKIP_DISTANCE_SHARE = 0.3f

/** A fling faster than this many widths per second decides a sideways swipe on its own. */
const val FLING_SHARE_PER_SECOND = 1f

/** The same for the player sheet, in screen heights per second: a short quick flick is enough to open or close it. */
const val SHEET_FLING_SHARE_PER_SECOND = 0.3f

/** Share of the path at which a released sheet goes on to open rather than back. */
const val SHEET_HALF_WAY = 0.5f

/** How much of the finger movement the cover follows where there is no track to switch to. */
const val RUBBER_BAND = 0.25f

/**
 * Cover offset for a finger that has moved [rawOffsetPx] (right is positive). Where there is no
 * track on that side the cover resists and follows only a part of the movement.
 */
fun dampedOffset(
    rawOffsetPx: Float,
    canPrevious: Boolean,
    canNext: Boolean,
): Float {
    val allowed = if (rawOffsetPx > 0f) canPrevious else canNext
    return if (allowed) rawOffsetPx else rawOffsetPx * RUBBER_BAND
}

/**
 * Decides a horizontal swipe on release: right means the previous track, left the next one.
 * The swipe counts when the drag passed [SKIP_DISTANCE_SHARE] of [widthPx], or when a fling in the
 * direction of the drag was fast enough. A side without a track never switches.
 */
fun skipResult(
    rawOffsetPx: Float,
    velocityPxPerSec: Float,
    widthPx: Float,
    canPrevious: Boolean,
    canNext: Boolean,
): SwipeSkip {
    if (widthPx <= 0f || rawOffsetPx == 0f) return SwipeSkip.NONE
    val goesRight = rawOffsetPx > 0f
    val farEnough = abs(rawOffsetPx) >= widthPx * SKIP_DISTANCE_SHARE
    val flung = velocityPxPerSec * rawOffsetPx > 0f && abs(velocityPxPerSec) >= widthPx * FLING_SHARE_PER_SECOND
    if (!farEnough && !flung) return SwipeSkip.NONE
    return when {
        goesRight && canPrevious -> SwipeSkip.PREVIOUS
        !goesRight && canNext -> SwipeSkip.NEXT
        else -> SwipeSkip.NONE
    }
}

/**
 * Whether a vertical sheet (the player) ends open after release: [progress] is 0 closed to 1 open,
 * [velocityPxPerSec] is positive downwards. A fast fling decides alone, otherwise the half way mark does.
 */
fun sheetSettlesOpen(
    progress: Float,
    velocityPxPerSec: Float,
    heightPx: Float,
): Boolean {
    if (heightPx > 0f && abs(velocityPxPerSec) >= heightPx * SHEET_FLING_SHARE_PER_SECOND) return velocityPxPerSec < 0f
    return progress >= SHEET_HALF_WAY
}
