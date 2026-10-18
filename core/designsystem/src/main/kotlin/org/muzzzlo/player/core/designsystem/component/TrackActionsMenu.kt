package org.muzzzlo.player.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors

/**
 * Menu of actions for one track. Not in the mockup: built from the sort dialog
 * (DESIGN.md, section 7). The menu closes itself after a choice through [onDismiss].
 *
 * @param actions items to show; the queue screen leaves out [TrackAction.OpenQueue].
 */
@Composable
fun TrackActionsMenu(
    title: String,
    artists: String,
    onAction: (TrackAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    actions: List<TrackAction> = TrackAction.entries,
) {
    MuzzzloDialog(title = title, onDismiss = onDismiss, modifier = modifier) {
        if (artists.isNotEmpty()) {
            Text(
                text = artists,
                style = MaterialTheme.typography.bodyMedium,
                color = MuzzzloColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        actions.forEach { action ->
            ActionRow(
                icon = action.icon,
                text = stringResource(action.label),
                onClick = {
                    onDismiss()
                    onAction(action)
                },
            )
        }
    }
}
