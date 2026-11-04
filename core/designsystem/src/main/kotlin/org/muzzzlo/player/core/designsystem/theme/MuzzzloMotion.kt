package org.muzzzlo.player.core.designsystem.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * Motion tokens of the app (task 2.0.9, DESIGN.md, section 6). After a gesture things move on springs, which pick up
 * the speed of the finger; changes that no finger started use the durations here.
 */
object MuzzzloMotion {
    /** Small changes: a cover fades in, a line lights up, a pulled cover comes back. */
    const val SHORT_MS = 200

    /** Screen transitions, the player sheet, the waveform appearing. */
    const val MEDIUM_MS = 300

    /** The accent following a new cover. */
    const val ACCENT_MS = 400

    /** The live background changing its colors. */
    const val BACKGROUND_MS = 1_500

    /** Standard easing of timed changes. */
    val Easing = FastOutSlowInEasing

    /** A timed change of [durationMs] with the standard easing. */
    fun <T> timed(durationMs: Int = MEDIUM_MS): FiniteAnimationSpec<T> = tween(durationMs, easing = Easing)

    /** What is let go after a drag (a cover, a sheet, a list row) settles on this spring: quick, without a bounce. */
    fun <T> gesture(): SpringSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = GESTURE_STIFFNESS)

    /** Rows of a list moving to new places, appearing and leaving. */
    val ItemPlacement: FiniteAnimationSpec<IntOffset> =
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = IntOffset(1, 1),
        )

    /** Fades of list rows that appear or leave. */
    val ItemFade: FiniteAnimationSpec<Float> = tween(SHORT_MS, easing = FastOutSlowInEasing)

    private const val GESTURE_STIFFNESS = 500f
}
