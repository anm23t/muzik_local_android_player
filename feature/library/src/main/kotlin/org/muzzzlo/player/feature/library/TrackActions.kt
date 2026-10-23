package org.muzzzlo.player.feature.library

import org.muzzzlo.player.core.designsystem.component.TrackAction
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.Track

/**
 * Runs an item of the track menu that the player handles. Navigation items are handled by the screen.
 */
internal fun PlayerController.onTrackAction(
    track: Track,
    action: TrackAction,
) {
    when (action) {
        TrackAction.PlayNext -> playNext(track)
        TrackAction.AddToQueue -> addToQueue(track)
        TrackAction.OpenQueue, TrackAction.EditTags, TrackAction.GoToArtist, TrackAction.Share -> Unit
        TrackAction.SleepTimer -> Unit
        TrackAction.AddToPlaylist, TrackAction.RemoveFromPlaylist -> Unit
        // Favorites and rating go through TrackMarksMenu, the menu does not report them.
        TrackAction.AddToFavorites,
        TrackAction.Rate,
        -> Unit
    }
}
