package org.muzzzlo.player.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors

/**
 * Menu of actions for one track. Not in the mockup: built from the sort dialog
 * (DESIGN.md, section 7). The menu closes itself after a choice through [onDismiss].
 *
 * @param actions items to show; the queue screen leaves out [TrackAction.OpenQueue].
 * @param artistNames artists of the track. "Go to artist" is shown only with [onOpenArtist] and at least one name:
 * one artist opens at once, several open [ArtistPickerMenu]. [onAction] also receives [TrackAction.GoToArtist].
 */
@Composable
fun TrackActionsMenu(
    title: String,
    artists: String,
    onAction: (TrackAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    actions: List<TrackAction> = TrackAction.entries,
    artistNames: List<String> = emptyList(),
    onOpenArtist: ((String) -> Unit)? = null,
) {
    var picking by remember { mutableStateOf(false) }
    if (picking) {
        ArtistPickerMenu(artists = artistNames, onPick = { onOpenArtist?.invoke(it) }, onDismiss = onDismiss)
        return
    }
    val canOpenArtist = onOpenArtist != null && artistNames.isNotEmpty()
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
        actions.filter { canOpenArtist || it != TrackAction.GoToArtist }.forEach { action ->
            ActionRow(
                icon = action.icon,
                text = stringResource(action.label),
                onClick = {
                    val tap = if (action == TrackAction.GoToArtist) ArtistTap.of(artistNames) else ArtistTap.Nothing
                    if (tap is ArtistTap.Choose) {
                        picking = true
                        return@ActionRow
                    }
                    onDismiss()
                    onAction(action)
                    if (tap is ArtistTap.Open) onOpenArtist?.invoke(tap.name)
                },
            )
        }
    }
}
