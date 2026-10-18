package org.muzzzlo.player.feature.library

import org.muzzzlo.player.core.designsystem.component.TrackAction
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.Track

/**
 * Runs an item of the track menu that the player handles. Navigation items are handled by the screen,
 * the rest are placeholders until their tasks.
 */
internal fun PlayerController.onTrackAction(
    track: Track,
    action: TrackAction,
) {
    when (action) {
        TrackAction.PlayNext -> playNext(track)
        TrackAction.AddToQueue -> addToQueue(track)
        TrackAction.OpenQueue, TrackAction.EditTags -> Unit
        // Placeholders: playlists, favorites and rating come in 1.1.
        TrackAction.AddToPlaylist,
        TrackAction.AddToFavorites,
        TrackAction.Rate,
        -> Unit
    }
}
