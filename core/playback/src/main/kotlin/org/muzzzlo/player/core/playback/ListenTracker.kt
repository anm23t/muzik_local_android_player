package org.muzzzlo.player.core.playback

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import org.muzzzlo.player.core.model.ListenEnd
import org.muzzzlo.player.core.model.ListenRecorder
import org.muzzzlo.player.core.model.ListenSession

/**
 * Reports the listens of the session player to [recorder] (ARCHITECTURE.md, section 7). The measuring is done by
 * [ListenSession]; this class feeds it from the player. [CrossfadePlayer] changes its current track at the start of
 * the overlap, so with crossfade a track counts as finished when its fade-out starts.
 *
 * Everything runs on the thread of the player.
 */
internal class ListenTracker(
    private val player: Player,
    recorder: ListenRecorder,
) : Player.Listener {
    private val session = ListenSession(SystemClock::elapsedRealtime, System::currentTimeMillis, recorder::record)

    fun start() {
        player.addListener(this)
        session.onTrackChanged(player.currentMediaItem?.toTrack(), ListenEnd.MANUAL)
        session.onPlayingChanged(player.isPlaying)
    }

    /** The service goes away: the listen in progress is reported as interrupted. */
    fun stop() {
        player.removeListener(this)
        session.finish(ListenEnd.INTERRUPTED)
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        session.onTrackChanged(mediaItem?.toTrack(), listenEndOf(reason))
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        session.onPlayingChanged(isPlaying)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_ENDED -> session.finish(ListenEnd.COMPLETED)
            // Stopped by the user, or by an error of the file.
            Player.STATE_IDLE -> session.finish(ListenEnd.MANUAL)
        }
    }
}

/**
 * How the listen of the previous track ended, by the reason the player gives for moving to another item: an
 * automatic move (including the start of a crossfade) and a repeat finish the track, everything else is the user.
 */
internal fun listenEndOf(transitionReason: Int): ListenEnd =
    when (transitionReason) {
        Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
        Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT,
        -> ListenEnd.COMPLETED
        else -> ListenEnd.MANUAL
    }
