package org.muzzzlo.player.core.model

import kotlinx.coroutines.flow.StateFlow

/** What happens when the queue reaches its end or a track ends. */
enum class RepeatMode { OFF, ALL, ONE }

/** Why the current track does not play. */
enum class PlaybackProblem {
    /** The file is gone or cannot be opened. */
    FILE_MISSING,

    /** The device cannot decode the format. */
    UNSUPPORTED,

    /** Any other failure. */
    OTHER,
}

/** Snapshot of the player that the UI renders. */
data class PlayerState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    /** Queue in its original order; with shuffle on the player visits it in a different order. */
    val queue: List<Track> = emptyList(),
    /** Index of [currentTrack] in [queue], -1 when the queue is empty. */
    val currentIndex: Int = -1,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    /** Set while [currentTrack] failed to play; the player has already moved on when there is a next track. */
    val problem: PlaybackProblem? = null,
)

/** Remote control of the playback service. Implemented in `core:playback`. */
interface PlayerController {
    val state: StateFlow<PlayerState>

    /** Connects to the playback service and restores the queue saved by the previous run. Safe to call twice. */
    fun connect()

    /** Replaces the queue with [tracks] and starts playing at [startIndex]. */
    fun play(
        tracks: List<Track>,
        startIndex: Int,
    )

    fun togglePlayPause()

    fun next()

    fun previous()

    fun seekTo(positionMs: Long)

    /** Inserts [track] right after the current one. Starts playing when the queue is empty. */
    fun playNext(track: Track)

    /** Appends [track] to the end of the queue. Starts playing when the queue is empty. */
    fun addToQueue(track: Track)

    /** Jumps to the queue item at [index] and plays it. */
    fun skipTo(index: Int)

    fun moveQueueItem(
        from: Int,
        to: Int,
    )

    fun removeQueueItem(index: Int)

    fun toggleShuffle()

    /** Cycles off, repeat all, repeat one. */
    fun cycleRepeatMode()
}
