package org.muzzzlo.player.core.playback

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.muzzzlo.player.core.model.SleepTimer
import org.muzzzlo.player.core.model.SleepTimerMode
import org.muzzzlo.player.core.model.SleepTimerRules
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SleepTimer] for the whole process: the screen starts and cancels it, [SleepTimerDriver] in the playback service
 * fades and pauses. The clock is the monotonic one, so a change of the device time does not move the end.
 */
@Singleton
internal class DefaultSleepTimer(
    private val clock: () -> Long,
) : SleepTimer {
    @Inject
    constructor() : this(SystemClock::elapsedRealtime)

    private val current = MutableStateFlow<SleepTimerMode?>(null)
    override val mode: StateFlow<SleepTimerMode?> = current.asStateFlow()

    override fun nowMs(): Long = clock()

    override fun start(minutes: Int) {
        current.value = SleepTimerRules.after(minutes, clock())
    }

    override fun startEndOfTrack() {
        current.value = SleepTimerMode.EndOfTrack
    }

    override fun cancel() {
        current.value = null
    }
}
