package org.muzzzlo.player.core.model

/**
 * Follows the current track of the player and measures one listen at a time (ARCHITECTURE.md, section 7).
 *
 * A listen opens when the current track starts to sound and closes when the track changes, the playback ends or
 * [finish] is called. Only the time while the track really plays is counted; a seek inside the track does not close
 * the listen. With crossfade the player changes its current track at the start of the overlap, so the outgoing track
 * is finished at that moment.
 *
 * Not thread-safe; use it on the thread of the player.
 *
 * @param elapsedMs a monotonic clock for the played time.
 * @param wallClockMs the wall clock for [FinishedListen.startedAtMs].
 * @param onFinished receives every closed listen that played at all.
 */
class ListenSession(
    private val elapsedMs: () -> Long,
    private val wallClockMs: () -> Long,
    private val onFinished: (FinishedListen) -> Unit,
) {
    private var track: Track? = null
    private var playlistId: Long? = null
    private var playing = false
    private var open: OpenListen? = null

    /**
     * The current track changed to [newTrack] (null: the queue is empty); the previous listen ends with [end].
     * [fromPlaylist] is the playlist the new queue item came from.
     */
    fun onTrackChanged(
        newTrack: Track?,
        end: ListenEnd,
        fromPlaylist: Long? = null,
    ) {
        finish(end)
        track = newTrack
        playlistId = fromPlaylist
        if (playing) openListen()
    }

    /** The player started or stopped to sound: play, pause, buffering, loss of audio focus. */
    fun onPlayingChanged(isPlaying: Boolean) {
        if (isPlaying == playing) return
        playing = isPlaying
        val current = open
        when {
            isPlaying && current == null -> openListen()
            isPlaying -> current?.resume(elapsedMs())
            else -> current?.pause(elapsedMs())
        }
    }

    /** Closes the listen in progress, if any; the next one opens when the track sounds again. */
    fun finish(end: ListenEnd) {
        val current = open ?: return
        open = null
        val playedMs = current.playedMs(elapsedMs())
        if (playedMs > 0) {
            onFinished(FinishedListen(current.track, current.startedAtMs, playedMs, end, current.playlistId))
        }
    }

    private fun openListen() {
        val current = track ?: return
        open = OpenListen(current, playlistId, wallClockMs(), elapsedMs())
    }

    private class OpenListen(
        val track: Track,
        val playlistId: Long?,
        val startedAtMs: Long,
        nowMs: Long,
    ) {
        private var totalMs = 0L
        private var since: Long? = nowMs

        fun pause(nowMs: Long) {
            totalMs = playedMs(nowMs)
            since = null
        }

        fun resume(nowMs: Long) {
            if (since == null) since = nowMs
        }

        fun playedMs(nowMs: Long): Long = totalMs + (since?.let { (nowMs - it).coerceAtLeast(0) } ?: 0)
    }
}
