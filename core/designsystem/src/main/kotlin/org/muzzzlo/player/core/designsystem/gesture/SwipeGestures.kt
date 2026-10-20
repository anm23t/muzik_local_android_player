package org.muzzzlo.player.core.designsystem.gesture

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.animation.core.animate as animateFloat

/**
 * Receiver of one axis of a swipe. [onDrag] gets the finger movement in px since the last call,
 * [onEnd] the speed in px/s along the axis and the size of the area along it.
 */
class AxisSwipe(
    val onDrag: (Float) -> Unit,
    val onEnd: (velocityPxPerSec: Float, extentPx: Float) -> Unit,
)

/**
 * Detects a swipe along the axes that have a receiver. The axis is chosen by the first movement past
 * the touch slop and kept until the finger lifts. Movement along a disabled axis is left alone, so a
 * parent can take it; taps and long presses are untouched.
 */
fun Modifier.swipeGestures(
    horizontal: AxisSwipe? = null,
    vertical: AxisSwipe? = null,
): Modifier =
    composed {
        val currentHorizontal by rememberUpdatedState(horizontal)
        val currentVertical by rememberUpdatedState(vertical)
        pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var isHorizontal = false
                var firstDelta = 0f
                val slop =
                    awaitTouchSlopOrCancellation(down.id) { change, over ->
                        val horizontalMove = abs(over.x) > abs(over.y)
                        val allowed = if (horizontalMove) currentHorizontal != null else currentVertical != null
                        if (allowed) {
                            isHorizontal = horizontalMove
                            firstDelta = if (horizontalMove) over.x else over.y
                            change.consume()
                        }
                    } ?: return@awaitEachGesture
                val swipe = (if (isHorizontal) currentHorizontal else currentVertical) ?: return@awaitEachGesture
                val extent = (if (isHorizontal) size.width else size.height).toFloat()
                val tracker = VelocityTracker()
                tracker.addPosition(slop.uptimeMillis, slop.position)
                swipe.onDrag(firstDelta)
                val finished =
                    drag(slop.id) { change ->
                        val delta = change.positionChange()
                        swipe.onDrag(if (isHorizontal) delta.x else delta.y)
                        tracker.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                    }
                val velocity = tracker.calculateVelocity()
                swipe.onEnd(
                    if (!finished) {
                        0f
                    } else if (isHorizontal) {
                        velocity.x
                    } else {
                        velocity.y
                    },
                    extent,
                )
            }
        }
    }

/**
 * Horizontal position of a cover that follows the finger and switches tracks (1.0.5). Feed it from
 * [swipeGestures] through [axis]; read [offsetPx] to move the cover. When a swipe counts, the cover
 * flies out, [onSkip] is called, and the cover comes in from the other side.
 */
@Stable
class CoverSwipeState(
    private val scope: CoroutineScope,
    private val canPrevious: () -> Boolean,
    private val canNext: () -> Boolean,
    private val onSkip: (SwipeSkip) -> Unit,
) {
    var offsetPx by mutableFloatStateOf(0f)
        private set
    private var rawPx = 0f
    private var job: Job? = null

    /** Receiver for [swipeGestures]. */
    val axis = AxisSwipe(onDrag = ::drag, onEnd = ::release)

    private fun drag(deltaPx: Float) {
        if (job?.isActive == true) {
            job?.cancel()
            rawPx = offsetPx
        }
        rawPx += deltaPx
        offsetPx = dampedOffset(rawPx, canPrevious(), canNext())
    }

    private fun release(
        velocityPxPerSec: Float,
        widthPx: Float,
    ) {
        val result = skipResult(rawPx, velocityPxPerSec, widthPx, canPrevious(), canNext())
        rawPx = 0f
        job =
            scope.launch {
                if (result == SwipeSkip.NONE) {
                    settle(0f)
                } else {
                    val outside = if (result == SwipeSkip.NEXT) -widthPx else widthPx
                    settle(outside)
                    onSkip(result)
                    offsetPx = -outside
                    settle(0f)
                }
            }
    }

    private suspend fun settle(target: Float) {
        animateFloat(offsetPx, target, animationSpec = tween(SETTLE_MS)) { value, _ ->
            offsetPx = value
        }
    }

    private companion object {
        const val SETTLE_MS = 160
    }
}
