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
import org.muzzzlo.player.core.designsystem.R
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors

/**
 * Menu of actions for one track. Not in the mockup: built from the sort dialog
 * (DESIGN.md, section 7). The menu closes itself after a choice through [onDismiss].
 *
 * @param actions items to show; the queue screen leaves out [TrackAction.OpenQueue], the playlist card adds
 * [TrackAction.RemoveFromPlaylist].
 * @param artists names of the artists, shown under the title.
 * @param loadArtists loads the artists of the track with their photos when the menu opens. "Go to artist" is shown
 * only with [onOpenArtist] and at least one artist: one artist opens at once (by id), several open
 * [ArtistPickerMenu]. [onAction] also receives [TrackAction.GoToArtist].
 * @param addToPlaylist makes "Add to playlist" open [PlaylistPickerMenu] and then, for a new playlist,
 * [PlaylistNameDialog]; without it the item is not shown. [onAction] also receives [TrackAction.AddToPlaylist].
 */
@Composable
fun TrackActionsMenu(
    title: String,
    artists: String,
    onAction: (TrackAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    actions: List<TrackAction> = TrackAction.Default,
    loadArtists: (suspend () -> List<ArtistChoice>)? = null,
    onOpenArtist: ((Long) -> Unit)? = null,
    addToPlaylist: AddToPlaylist? = null,
) {
    var step by remember { mutableStateOf(MenuStep.Actions) }
    var artistChoices by remember { mutableStateOf<List<ArtistChoice>>(emptyList()) }
    LaunchedEffect(Unit) { artistChoices = loadArtists?.invoke().orEmpty() }
    when (step) {
        MenuStep.Actions -> Unit
        MenuStep.Artists -> {
            ArtistPickerMenu(artists = artistChoices, onPick = { onOpenArtist?.invoke(it.id) }, onDismiss = onDismiss)
            return
        }
        MenuStep.Playlists, MenuStep.NewPlaylist -> {
            addToPlaylist?.let { PlaylistSteps(it, step, onNew = { step = MenuStep.NewPlaylist }, onDismiss) }
            return
        }
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
        actions.filter { isShown(it, canOpenArtist, addToPlaylist != null) }.forEach { action ->
            ActionRow(
                icon = action.icon,
                text = stringResource(action.label),
                onClick = {
                    val tap = if (action == TrackAction.GoToArtist) ArtistTap.of(artistChoices) else ArtistTap.Nothing
                    if (tap is ArtistTap.Choose) {
                        step = MenuStep.Artists
                        return@ActionRow
                    }
                    if (action == TrackAction.AddToPlaylist) {
                        onAction(action)
                        step = MenuStep.Playlists
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

/** Which dialog of the menu is open: the actions, or a picker that replaced them. */
private enum class MenuStep { Actions, Artists, Playlists, NewPlaylist }

private fun isShown(
    action: TrackAction,
    canOpenArtist: Boolean,
    canAddToPlaylist: Boolean,
): Boolean =
    when (action) {
        TrackAction.GoToArtist -> canOpenArtist
        TrackAction.AddToPlaylist -> canAddToPlaylist
        else -> true
    }

@Composable
private fun PlaylistSteps(
    target: AddToPlaylist,
    step: MenuStep,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    var playlists by remember { mutableStateOf<List<PlaylistChoice>?>(null) }
    LaunchedEffect(Unit) { playlists = target.load() }
    if (step == MenuStep.NewPlaylist) {
        PlaylistNameDialog(
            title = stringResource(R.string.playlist_new),
            confirmLabel = stringResource(R.string.playlist_create),
            onConfirm = target.onCreate,
            onDismiss = onDismiss,
        )
    } else {
        // Shown once loaded, so the list does not jump from empty to full.
        playlists?.let {
            PlaylistPickerMenu(
                playlists = it,
                onPick = { playlist -> target.onAdd(playlist.id) },
                onNew = onNew,
                onDismiss = onDismiss,
            )
        }
    }
}
