package org.muzzzlo.player.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import org.muzzzlo.player.core.designsystem.gesture.CoverSwipeState
import org.muzzzlo.player.core.designsystem.gesture.SwipeSkip
import org.muzzzlo.player.core.model.PlayerState

/** Cover swipe that switches the queue (1.0.5); [state] is read at gesture time, so it is always current. */
@Composable
internal fun rememberCoverSwipe(
    viewModel: PlayerViewModel,
    state: () -> PlayerState,
): CoverSwipeState {
    val scope = rememberCoroutineScope()
    return remember {
        CoverSwipeState(
            scope = scope,
            canPrevious = { state().hasPrevious },
            canNext = { state().hasNext },
            onSkip = { if (it == SwipeSkip.NEXT) viewModel.onNext() else viewModel.onPrevious() },
        )
    }
}
