package org.muzzzlo.player.core.model

import kotlinx.coroutines.flow.StateFlow

/** What a running sleep timer waits for (task 1.2.9). */
sealed interface SleepTimerMode {
    /** Playback pauses at [endAtMs] of the monotonic clock of [SleepTimer.nowMs]. */
    data class After(
        val endAtMs: Long,
    ) : SleepTimerMode

    /** Playback pauses when the track that plays now ends; a track chosen by hand becomes the one to wait for. */
    data object EndOfTrack : SleepTimerMode
}

/**
 * What the playback should do on a tick of a running timer.
 *
 * @property volume share of the normal volume in [0, 1] while the timer fades the sound out.
 * @property pause the timer ran out: pause, restore the volume and clear the timer.
 */
data class SleepTimerStep(
    val volume: Float,
    val pause: Boolean,
)

/**
 * Rules of the sleep timer, pure Kotlin so they are tested on virtual time (ARCHITECTURE.md, section 6).
 *
 * - "After N minutes" counts on the monotonic clock, also while playback is paused. The last [FADE_MS] fade the
 *   sound out; at the end playback pauses. A timer that ends while paused only clears.
 * - "End of track" fades the last [TRACK_FADE_MS] before the end of the current track (before the crossfade, when
 *   one is planned) and pauses when the player moves on by itself ([pausesOnTransition]); the next track is put
 *   back to its start, so "Play" starts it from the beginning.
 */
object SleepTimerRules {
    const val FADE_MS = 30_000L
    const val TRACK_FADE_MS = 10_000L
    private const val MS_PER_MINUTE = 60_000L

    /** Lengths offered in the dialog, in minutes. */
    val MINUTE_OPTIONS = listOf(15, 30, 45, 60, 90, 120)

    /** A timer that ends [minutes] after [nowMs]. */
    fun after(
        minutes: Int,
        nowMs: Long,
    ): SleepTimerMode.After {
        require(minutes > 0) { "A sleep timer needs a length" }
        return SleepTimerMode.After(nowMs + minutes * MS_PER_MINUTE)
    }

    /**
     * Time left until the timer acts, at least 0; for "end of track" the time to [trackEndMs] (the end of the
     * track or the start of its crossfade), null while the length of the track is unknown.
     */
    fun remainingMs(
        mode: SleepTimerMode,
        nowMs: Long,
        positionMs: Long,
        trackEndMs: Long?,
    ): Long? =
        when (mode) {
            is SleepTimerMode.After -> (mode.endAtMs - nowMs).coerceAtLeast(0)
            SleepTimerMode.EndOfTrack -> trackEndMs?.takeIf { it > 0 }?.let { (it - positionMs).coerceAtLeast(0) }
        }

    /** The step of one tick; "end of track" never pauses here, the end of the track does ([pausesOnTransition]). */
    fun step(
        mode: SleepTimerMode,
        nowMs: Long,
        positionMs: Long,
        trackEndMs: Long?,
    ): SleepTimerStep {
        val remaining = remainingMs(mode, nowMs, positionMs, trackEndMs) ?: return SleepTimerStep(1f, pause = false)
        val fade = if (mode is SleepTimerMode.After) FADE_MS else TRACK_FADE_MS
        return SleepTimerStep(volumeFor(remaining, fade), pause = mode is SleepTimerMode.After && remaining == 0L)
    }

    /**
     * True when a change of the current track ends the timer: in "end of track" mode, when the player moved on by
     * itself (the end of the track, a crossfade or gapless). A track chosen by hand does not end it.
     */
    fun pausesOnTransition(
        mode: SleepTimerMode?,
        automatic: Boolean,
    ): Boolean = mode == SleepTimerMode.EndOfTrack && automatic

    /** Volume [remainingMs] before the end of a fade of [fadeMs]: 1 before it, falling to 0 as a square curve. */
    fun volumeFor(
        remainingMs: Long,
        fadeMs: Long,
    ): Float {
        if (fadeMs <= 0 || remainingMs >= fadeMs) return 1f
        val share = (remainingMs.toFloat() / fadeMs).coerceIn(0f, 1f)
        // The ear hears loudness roughly by the square of the amplitude, so the fade sounds even.
        return share * share
    }
}

/**
 * The sleep timer of the playback service (task 1.2.9). It lives in `core:playback` for the whole process, so it
 * runs on when the screen closes; the service fades the sound and pauses. Implemented in `core:playback`.
 */
interface SleepTimer {
    /** The running timer, null when none runs. */
    val mode: StateFlow<SleepTimerMode?>

    /** Now on the monotonic clock the timer counts on, in milliseconds. */
    fun nowMs(): Long

    /** Pauses [minutes] from now. */
    fun start(minutes: Int)

    /** Pauses at the end of the current track. */
    fun startEndOfTrack()

    fun cancel()
}
