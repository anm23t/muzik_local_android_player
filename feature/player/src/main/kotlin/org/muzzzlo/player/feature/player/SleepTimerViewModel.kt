package org.muzzzlo.player.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.SleepTimer
import org.muzzzlo.player.core.model.SleepTimerMode
import org.muzzzlo.player.core.model.SleepTimerRules
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L
private const val MS_PER_SECOND = 1_000L

/**
 * A running sleep timer as the player shows it.
 *
 * @property remainingMs time left; for "end of track" the rest of the current track, null while its length is
 *   unknown.
 */
data class SleepTimerUi(
    val mode: SleepTimerMode,
    val remainingMs: Long?,
)

/**
 * The sleep timer of the big player (task 1.2.9): the badge under the cover and the dialog of the menu. The timer
 * itself runs in the playback service; this only starts, cancels and shows it. "After N minutes" counts down once
 * a second, "end of track" follows the position of the player.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SleepTimerViewModel
    @Inject
    constructor(
        private val timer: SleepTimer,
        private val player: PlayerController,
    ) : ViewModel() {
        val state: StateFlow<SleepTimerUi?> =
            timer.mode
                .flatMapLatest(::uiOf)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        fun onMinutesChosen(minutes: Int) = timer.start(minutes)

        fun onEndOfTrackChosen() = timer.startEndOfTrack()

        fun onCancel() = timer.cancel()

        private fun uiOf(mode: SleepTimerMode?): Flow<SleepTimerUi?> =
            when (mode) {
                null -> flowOf(null)
                is SleepTimerMode.After ->
                    flow {
                        while (true) {
                            val remaining = SleepTimerRules.remainingMs(mode, timer.nowMs(), 0, null) ?: 0L
                            emit(SleepTimerUi(mode, remaining))
                            if (remaining == 0L) break
                            // Wake when the countdown reaches the next whole second, so the shown seconds do not skip.
                            delay((remaining % MS_PER_SECOND).takeIf { it > 0 } ?: MS_PER_SECOND)
                        }
                    }
                SleepTimerMode.EndOfTrack ->
                    player.state.map { state ->
                        val end = state.durationMs.takeIf { it > 0 }
                        SleepTimerUi(mode, SleepTimerRules.remainingMs(mode, 0, state.positionMs, end))
                    }
            }
    }
