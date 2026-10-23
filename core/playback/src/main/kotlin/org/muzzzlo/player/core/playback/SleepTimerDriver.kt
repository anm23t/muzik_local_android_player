package org.muzzzlo.player.core.playback

import android.os.Handler
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import org.muzzzlo.player.core.model.SleepTimer
import org.muzzzlo.player.core.model.SleepTimerMode
import org.muzzzlo.player.core.model.SleepTimerRules

/**
 * Carries out the sleep timer in the playback service (task 1.2.9): while a timer runs, every [TICK_MS] it sets the
 * fade volume of [player] from [SleepTimerRules.step] and pauses when the timer runs out; in "end of track" mode an
 * automatic move to the next track pauses and puts that track back to its start. Then the volume comes back and the
 * timer clears. All calls come on the looper of [player] ([handler]).
 */
@UnstableApi
internal class SleepTimerDriver(
    private val player: CrossfadePlayer,
    private val timer: SleepTimer,
    private val handler: Handler,
) : Player.Listener {
    private var mode: SleepTimerMode? = null
    private val tick = Runnable { onTick() }

    fun start() = player.addListener(this)

    fun stop() {
        handler.removeCallbacks(tick)
        player.removeListener(this)
        player.sleepVolume = 1f
    }

    /** A new value of [SleepTimer.mode]: a new timer starts ticking, a cancelled one gives the volume back. */
    fun onModeChanged(newMode: SleepTimerMode?) {
        mode = newMode
        handler.removeCallbacks(tick)
        if (newMode == null) {
            player.sleepVolume = 1f
        } else {
            onTick()
        }
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        val automatic = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        if (!SleepTimerRules.pausesOnTransition(mode, automatic)) return
        player.pause()
        player.seekTo(0)
        finish()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        // The queue ended on its own: nothing is left to pause.
        if (playbackState == Player.STATE_ENDED && mode == SleepTimerMode.EndOfTrack) finish()
    }

    private fun onTick() {
        val current = mode ?: return
        val step = SleepTimerRules.step(current, timer.nowMs(), player.currentPosition, player.trackEndMs)
        if (step.pause) {
            player.pause()
            finish()
            return
        }
        player.sleepVolume = step.volume
        handler.postDelayed(tick, TICK_MS)
    }

    private fun finish() {
        handler.removeCallbacks(tick)
        player.sleepVolume = 1f
        timer.cancel()
    }

    private companion object {
        const val TICK_MS = 250L
    }
}
