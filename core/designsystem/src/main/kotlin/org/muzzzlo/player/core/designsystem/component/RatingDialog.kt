package org.muzzzlo.player.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.muzzzlo.player.core.designsystem.R

/**
 * What the track menu needs for "Add to favorites" and "Rate" (task 1.1.6).
 *
 * @property load the marks of the track, loaded when the menu opens.
 * @property onFavorite sets the favorite flag to this value.
 * @property onRate sets the rating in tenths 0..100, or clears it with null.
 */
class TrackMarksMenu(
    val load: suspend () -> TrackMarksState,
    val onFavorite: (Boolean) -> Unit,
    val onRate: (Int?) -> Unit,
)

/** Favorite flag and rating of a track as the menu shows them; [ratingTenths] is null for an unrated track. */
data class TrackMarksState(
    val isFavorite: Boolean = false,
    val ratingTenths: Int? = null,
)

/**
 * Dialog for the rating of a track. Not in the mockup: the dialog of the sort dialog (15:111) with the plus and minus
 * row of the settings (41:894, 15:124), the bar of the player (3:20) and a wide button (15:142), DESIGN.md, section 7.
 * The bar sets the rating by a tap or a drag, minus and plus change it by 0.1. "Clear rating" is shown for a rated
 * track. The dialog closes itself through [onDismiss] after a choice.
 *
 * @param current the saved rating in tenths, null when not rated.
 * @param onRate the chosen rating in tenths, or null for "Clear rating".
 */
@Composable
fun RatingDialog(
    current: Int?,
    onRate: (Int?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable { mutableIntStateOf(current ?: RatingScale.DEFAULT) }
    MuzzzloDialog(title = stringResource(R.string.track_action_rate), onDismiss = onDismiss, modifier = modifier) {
        StepperRow(
            value = RatingScale.format(draft),
            onDecrease = { draft = RatingScale.step(draft, -1) },
            onIncrease = { draft = RatingScale.step(draft, 1) },
            decreaseDescription = stringResource(R.string.rating_decrease),
            increaseDescription = stringResource(R.string.rating_increase),
            canDecrease = draft > RatingScale.MIN,
            canIncrease = draft < RatingScale.MAX,
        )
        ProgressBar(
            progress = RatingScale.toShare(draft),
            onSeek = { draft = RatingScale.fromShare(it) },
        )
        PillButton(
            text = stringResource(R.string.rating_save),
            onClick = {
                onDismiss()
                onRate(draft)
            },
            wide = true,
        )
        if (current != null) {
            ActionRow(
                icon = R.drawable.ic_close,
                text = stringResource(R.string.rating_clear),
                onClick = {
                    onDismiss()
                    onRate(null)
                },
            )
        }
    }
}
