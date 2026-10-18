package org.muzzzlo.player.core.model

import kotlinx.coroutines.flow.StateFlow

/** Snapshot of the player that the UI renders. */
data class PlayerState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
)

/** Remote control of the playback service. Implemented in `core:playback`. */
interface PlayerController {
    val state: StateFlow<PlayerState>

    /** Replaces the queue with [tracks] and starts playing at [startIndex]. */
    fun play(
        tracks: List<Track>,
        startIndex: Int,
    )

    fun togglePlayPause()

    fun next()

    fun previous()

    fun seekTo(positionMs: Long)
}
