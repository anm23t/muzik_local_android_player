package org.muzzzlo.player

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import org.muzzzlo.player.core.designsystem.theme.MuzzzloMotion

private fun <T> motionSpec() = MuzzzloMotion.timed<T>()

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
