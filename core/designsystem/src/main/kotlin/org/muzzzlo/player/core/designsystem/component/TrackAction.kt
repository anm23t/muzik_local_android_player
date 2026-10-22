package org.muzzzlo.player.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.muzzzlo.player.core.designsystem.R

/**
 * Everything the track menu can do (DESIGN.md, section 3, "Поведение"), in display order.
 * A ViewModel receives the chosen item in `onTrackAction(track, action)`.
 */
enum class TrackAction(
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
) {
    PlayNext(R.drawable.ic_list_start, R.string.track_action_play_next),
    AddToQueue(R.drawable.ic_list_end, R.string.track_action_add_to_queue),

    /** Navigation: the screen opens the queue, the ViewModel has nothing to do. */
    OpenQueue(R.drawable.ic_list_music, R.string.track_action_open_queue),

    /** Navigation: [TrackActionsMenu] opens the artist card or the artist picker itself. */
    GoToArtist(R.drawable.ic_users, R.string.track_action_go_to_artist),

    /** Opens the playlist picker of [TrackActionsMenu]; the ViewModel has nothing to do. */
    AddToPlaylist(R.drawable.ic_list_plus, R.string.track_action_add_to_playlist),

    /** Only in the playlist card: removes this entry of the playlist. */
    RemoveFromPlaylist(R.drawable.ic_list_minus, R.string.track_action_remove_from_playlist),

    /** Toggles the favorite flag through [TrackMarksMenu]; the ViewModel has nothing to do. */
    AddToFavorites(R.drawable.ic_heart, R.string.track_action_add_to_favorites),

    /** Opens [RatingDialog]; the rating is saved through [TrackMarksMenu], the ViewModel has nothing to do. */
    Rate(R.drawable.ic_star, R.string.track_action_rate),

    /** Placeholder until the tag editor (0.3.6). */
    EditTags(R.drawable.ic_pencil, R.string.track_action_edit_tags),
    ;

    companion object {
        /** The items of every track menu; the playlist card adds [RemoveFromPlaylist]. */
        val Default: List<TrackAction> = entries - RemoveFromPlaylist
    }
}
