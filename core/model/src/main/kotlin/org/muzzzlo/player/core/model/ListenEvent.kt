package org.muzzzlo.player.core.model

/**
 * One listen of a track as the statistics store it (DATABASE.md, `listen_event`). The title and the artists are a
 * snapshot taken at the time of the listen, so the statistics survive renamed and deleted files.
 *
 * @property trackId null once the track is gone from the library.
 * @property startedAtMs wall clock time when the track started to sound.
 * @property playedMs time the track really played: pauses, buffering and seeks forward add nothing.
 * @property durationMs length of the track at the time of the listen.
 * @property counted the listen reached the threshold ([ListenRules.thresholdMs]).
 * @property skipped the listen was ended by the user before it was counted.
 */
data class ListenEvent(
    val id: Long = 0,
    val trackId: Long?,
    val startedAtMs: Long,
    val playedMs: Long,
    val durationMs: Long,
    val counted: Boolean,
    val skipped: Boolean,
    val titleSnapshot: String,
    val artistsSnapshot: List<String>,
)

/** How a listen ended. */
enum class ListenEnd {
    /** The track played to its end, or to the start of the crossfade into the next track, or repeated itself. */
    COMPLETED,

    /** The user moved on: next, previous, another track of the queue, a new queue or stop. */
    MANUAL,

    /** The playback service went away in the middle of the track. */
    INTERRUPTED,
}

/** A listen as the player reports it, before the statistics apply their settings ([ListenRules.eventOf]). */
data class FinishedListen(
    val track: Track,
    val startedAtMs: Long,
    val playedMs: Long,
    val end: ListenEnd,
)

/**
 * Receives the listens of the player (`core:playback`) and stores them (`core:stats`). Called on the player thread,
 * so an implementation must not block it.
 */
interface ListenRecorder {
    fun record(listen: FinishedListen)
}
