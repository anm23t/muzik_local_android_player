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

    /** Placeholder until playlists (1.1). */
    AddToPlaylist(R.drawable.ic_list_plus, R.string.track_action_add_to_playlist),

    /** Placeholder until favorites (1.1). */
    AddToFavorites(R.drawable.ic_heart, R.string.track_action_add_to_favorites),

    /** Placeholder until the rating dialog (1.1). */
    Rate(R.drawable.ic_star, R.string.track_action_rate),

    /** Placeholder until the tag editor (0.3.6). */
    EditTags(R.drawable.ic_pencil, R.string.track_action_edit_tags),
}
