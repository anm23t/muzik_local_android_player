package org.muzzzlo.player.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import org.muzzzlo.player.core.designsystem.component.AddToPlaylist
import org.muzzzlo.player.core.designsystem.component.ArtistChoice
import org.muzzzlo.player.core.designsystem.component.TrackAction
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.PlayerState
import org.muzzzlo.player.core.model.PlaylistRepository
import org.muzzzlo.player.core.model.Track
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        private val player: PlayerController,
        private val library: LibraryRepository,
        private val playlists: PlaylistRepository,
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

        /** Artists of the track with their photos, for the artist picker. */
        suspend fun artistChoices(trackId: Long): List<ArtistChoice> =
            library.artistsOfTrack(trackId).map { ArtistChoice(it.id, it.name, it.photoPath) }

        /** "Add to playlist" of the track menu. */
        fun addToPlaylist(trackId: Long): AddToPlaylist = playlists.addToPlaylist(viewModelScope, trackId)

        /** Item chosen in the track menu. Navigation items are handled by the screen. */
        fun onTrackAction(
            track: Track,
            action: TrackAction,
        ) {
            when (action) {
                TrackAction.PlayNext -> player.playNext(track)
                TrackAction.AddToQueue -> player.addToQueue(track)
                TrackAction.OpenQueue, TrackAction.EditTags, TrackAction.GoToArtist, TrackAction.AddToPlaylist -> Unit
                // Placeholders: favorites and rating come in 1.1.6.
                TrackAction.AddToFavorites,
                TrackAction.Rate,
                -> Unit
            }
        }
    }
