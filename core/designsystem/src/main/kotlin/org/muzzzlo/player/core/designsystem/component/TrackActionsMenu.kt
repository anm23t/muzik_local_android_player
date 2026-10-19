package org.muzzzlo.player.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
 * @param artists names of the artists, shown under the title.
 * @param loadArtists loads the artists of the track with their photos when the menu opens. "Go to artist" is shown
 * only with [onOpenArtist] and at least one artist: one artist opens at once (by id), several open
 * [ArtistPickerMenu]. [onAction] also receives [TrackAction.GoToArtist].
 */
@Composable
fun TrackActionsMenu(
    title: String,
    artists: String,
    onAction: (TrackAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    actions: List<TrackAction> = TrackAction.entries,
    loadArtists: (suspend () -> List<ArtistChoice>)? = null,
    onOpenArtist: ((Long) -> Unit)? = null,
) {
    var picking by remember { mutableStateOf(false) }
    var artistChoices by remember { mutableStateOf<List<ArtistChoice>>(emptyList()) }
    LaunchedEffect(Unit) { artistChoices = loadArtists?.invoke().orEmpty() }
    if (picking) {
        ArtistPickerMenu(artists = artistChoices, onPick = { onOpenArtist?.invoke(it.id) }, onDismiss = onDismiss)
        return
    }
    val canOpenArtist = onOpenArtist != null && artistChoices.isNotEmpty()
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
                    val tap = if (action == TrackAction.GoToArtist) ArtistTap.of(artistChoices) else ArtistTap.Nothing
                    if (tap is ArtistTap.Choose) {
                        picking = true
                        return@ActionRow
                    }
                    onDismiss()
                    onAction(action)
                    if (tap is ArtistTap.Open) onOpenArtist?.invoke(tap.artist.id)
                },
            )
        }
    }
}
