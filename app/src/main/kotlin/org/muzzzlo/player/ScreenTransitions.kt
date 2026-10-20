package org.muzzzlo.player

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry

private const val SCREEN_TRANSITION_MS = 300

private fun <T> motionSpec() = tween<T>(SCREEN_TRANSITION_MS, easing = FastOutSlowInEasing)

private typealias NavScope = AnimatedContentTransitionScope<NavBackStackEntry>

/**
 * Page transitions for the whole `NavHost`; the system back gesture plays the pop ones under the finger.
 * The player is not a page: it is a layer over the `NavHost` ([PlayerSheetState]).
 */
internal object ScreenTransitions {
    val enter: NavScope.() -> EnterTransition = { slideInHorizontally(motionSpec<IntOffset>()) { it } }

    val exit: NavScope.() -> ExitTransition = { slideOutHorizontally(motionSpec<IntOffset>()) { -it } }

    val popEnter: NavScope.() -> EnterTransition = { slideInHorizontally(motionSpec<IntOffset>()) { -it } }

    val popExit: NavScope.() -> ExitTransition = { slideOutHorizontally(motionSpec<IntOffset>()) { it } }
}
