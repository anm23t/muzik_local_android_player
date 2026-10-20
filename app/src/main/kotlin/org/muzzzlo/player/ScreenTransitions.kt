package org.muzzzlo.player

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry

/** How one navigation step moves the screens (ARCHITECTURE.md, section 9). */
internal enum class ScreenMotion {
    /** The new screen slides in from the right and pushes the old one to the left; back is the mirror. */
    PAGE,

    /** The player rises from the bottom over the screen below, which stays in place; back lowers it. */
    PLAYER,
}

/**
 * Picks the motion for a step from [from] to [to].
 * The player moves vertically only when it is the screen that arrives (forward) or leaves ([isPop]);
 * steps between the player and screens opened from it, like the queue, are ordinary pages.
 */
internal fun screenMotion(
    from: String?,
    to: String?,
    isPop: Boolean,
    playerRoute: String,
): ScreenMotion {
    val playerMoves = if (isPop) from == playerRoute else to == playerRoute
    return if (playerMoves) ScreenMotion.PLAYER else ScreenMotion.PAGE
}

private const val SCREEN_TRANSITION_MS = 300

private fun <T> motionSpec() = tween<T>(SCREEN_TRANSITION_MS, easing = FastOutSlowInEasing)

private typealias NavScope = AnimatedContentTransitionScope<NavBackStackEntry>

private fun NavScope.motion(isPop: Boolean): ScreenMotion =
    screenMotion(initialState.destination.route, targetState.destination.route, isPop, ROUTE_PLAYER)

/** Transitions for the whole `NavHost`; the system back gesture plays the pop ones under the finger. */
internal object ScreenTransitions {
    val enter: NavScope.() -> EnterTransition = {
        when (motion(isPop = false)) {
            ScreenMotion.PAGE -> slideInHorizontally(motionSpec<IntOffset>()) { it }
            ScreenMotion.PLAYER -> slideInVertically(motionSpec<IntOffset>()) { it }
        }
    }

    val exit: NavScope.() -> ExitTransition = {
        when (motion(isPop = false)) {
            ScreenMotion.PAGE -> slideOutHorizontally(motionSpec<IntOffset>()) { -it }
            ScreenMotion.PLAYER -> ExitTransition.KeepUntilTransitionsFinished
        }
    }

    val popEnter: NavScope.() -> EnterTransition = {
        when (motion(isPop = true)) {
            ScreenMotion.PAGE -> slideInHorizontally(motionSpec<IntOffset>()) { -it }
            ScreenMotion.PLAYER -> EnterTransition.None
        }
    }

    val popExit: NavScope.() -> ExitTransition = {
        when (motion(isPop = true)) {
            ScreenMotion.PAGE -> slideOutHorizontally(motionSpec<IntOffset>()) { it }
            ScreenMotion.PLAYER -> slideOutVertically(motionSpec<IntOffset>()) { it }
        }
    }
}
