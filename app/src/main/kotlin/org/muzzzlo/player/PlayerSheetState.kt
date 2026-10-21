package org.muzzzlo.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.muzzzlo.player.core.designsystem.gesture.sheetSettlesOpen

private const val SHEET_MS = 300

/**
 * Position of the player layer over the `NavHost` (ARCHITECTURE.md, section 9): [progress] is 0 when
 * the player is closed and 1 when it is open. A finger moves it through [dragBy] and [release];
 * buttons and the back key use [open] and [close], which animate.
 */
@Stable
internal class PlayerSheetState(private val scope: CoroutineScope) {
    /** Height of the layer in px; the drag maps the finger movement to a share of it. */
    var heightPx by mutableFloatStateOf(0f)

    var progress by mutableFloatStateOf(0f)
        private set
    private var job: Job? = null

    /** Where the current drag began, null between drags; [release] uses it to know the direction. */
    private var dragStart: Float? = null

    /** [deltaPx] is the finger movement since the last call, positive downwards. */
    fun dragBy(deltaPx: Float) {
        if (heightPx <= 0f) return
        job?.cancel()
        if (dragStart == null) dragStart = progress
        progress = (progress - deltaPx / heightPx).coerceIn(0f, 1f)
    }

    /** Sets the position directly, for the system back gesture that follows the finger. */
    fun moveTo(value: Float) {
        job?.cancel()
        progress = value.coerceIn(0f, 1f)
    }

    /** Ends a drag; [velocityPxPerSec] is positive downwards. */
    fun release(velocityPxPerSec: Float) {
        val start = dragStart ?: progress
        dragStart = null
        if (sheetSettlesOpen(progress, velocityPxPerSec, heightPx, start)) open() else close()
    }

    fun open() = animateTo(1f)

    fun close() = animateTo(0f)

    /** Hides the player at once, for a screen that slides in over it. */
    fun hide() = moveTo(0f)

    private fun animateTo(target: Float) {
        job?.cancel()
        job =
            scope.launch {
                animate(progress, target, animationSpec = tween(SHEET_MS, easing = FastOutSlowInEasing)) { value, _ ->
                    progress = value
                }
            }
    }
}
