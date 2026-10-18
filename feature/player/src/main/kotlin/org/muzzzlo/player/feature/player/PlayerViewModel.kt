package org.muzzzlo.player.feature.player

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import org.muzzzlo.player.core.designsystem.component.TrackAction
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.PlayerState
import org.muzzzlo.player.core.model.Track
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        private val player: PlayerController,
    ) : ViewModel() {
        val state: StateFlow<PlayerState> = player.state

        fun onPlayPause() = player.togglePlayPause()

        fun onNext() = player.next()

        fun onPrevious() = player.previous()

        fun onSeek(positionMs: Long) = player.seekTo(positionMs)

        fun onToggleShuffle() = player.toggleShuffle()

        fun onCycleRepeat() = player.cycleRepeatMode()

        fun onQueueItemClicked(index: Int) = player.skipTo(index)

        fun onQueueItemMoved(
            from: Int,
            to: Int,
        ) = player.moveQueueItem(from, to)

        fun onQueueItemRemoved(index: Int) = player.removeQueueItem(index)

        /** Item chosen in the track menu. Navigation items are handled by the screen. */
        fun onTrackAction(
            track: Track,
            action: TrackAction,
        ) {
            when (action) {
                TrackAction.PlayNext -> player.playNext(track)
                TrackAction.AddToQueue -> player.addToQueue(track)
                TrackAction.OpenQueue -> Unit
                // Placeholders: playlists, favorites and rating come in 1.1, the tag editor in 0.3.6.
                TrackAction.AddToPlaylist,
                TrackAction.AddToFavorites,
                TrackAction.Rate,
                TrackAction.EditTags,
                -> Unit
            }
        }
    }
