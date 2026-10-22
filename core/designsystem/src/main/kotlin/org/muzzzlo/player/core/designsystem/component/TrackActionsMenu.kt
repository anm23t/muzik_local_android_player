package org.muzzzlo.player.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
 * @param marks makes "Add to favorites" (or "Remove from favorites") and "Rate" work; without it the two items are
 * not shown. [onAction] does not receive them.
 * @param shareUri the `content://` address of the track's file; makes "Share" open the system share sheet, without it
 * the item is not shown. [onAction] does not receive it.
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
    marks: TrackMarksMenu? = null,
    shareUri: String? = null,
) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(MenuStep.Actions) }
    var artistChoices by remember { mutableStateOf<List<ArtistChoice>>(emptyList()) }
    var markState by remember { mutableStateOf(TrackMarksState()) }
    LaunchedEffect(Unit) {
        artistChoices = loadArtists?.invoke().orEmpty()
        marks?.let { markState = it.load() }
    }
    val replaced =
        StepDialog(
            step = step,
            artistChoices = artistChoices,
            currentRating = markState.ratingTenths,
            onOpenArtist = onOpenArtist,
            onRate = { marks?.onRate?.invoke(it) },
            addToPlaylist = addToPlaylist,
            onNewPlaylist = { step = MenuStep.NewPlaylist },
            onDismiss = onDismiss,
        )
    if (replaced) return
    val canOpenArtist = onOpenArtist != null && artistChoices.isNotEmpty()
    MuzzzloDialog(title = title, onDismiss = onDismiss, modifier = modifier) {
        ArtistsLine(artists)
        val optional = optionalActions(canOpenArtist, addToPlaylist != null, marks != null, shareUri != null)
        actions.filter { it !in OPTIONAL_ACTIONS || it in optional }.forEach { action ->
            ActionRow(
                icon = action.icon,
                text = stringResource(labelOf(action, markState.isFavorite)),
                onClick = {
                    val tap = if (action == TrackAction.GoToArtist) ArtistTap.of(artistChoices) else ArtistTap.Nothing
                    val next = stepAfter(action, tap)
                    when {
                        action == TrackAction.AddToFavorites -> {
                            onDismiss()
                            marks?.onFavorite?.invoke(!markState.isFavorite)
                        }
                        action == TrackAction.Share -> {
                            onDismiss()
                            shareUri?.let { context.shareTracks(listOf(it)) }
                        }
                        next != null -> {
                            if (action == TrackAction.AddToPlaylist) onAction(action)
                            step = next
                        }
                        else -> {
                            onDismiss()
                            onAction(action)
                            if (tap is ArtistTap.Open) onOpenArtist?.invoke(tap.artist.id)
                        }
                    }
                },
            )
        }
    }
}

/** Which dialog of the menu is open: the actions, or a picker that replaced them. */
private enum class MenuStep { Actions, Artists, Playlists, NewPlaylist, Rating }

/** The artists under the title; nothing for a track without any. */
@Composable
private fun ArtistsLine(artists: String) {
    if (artists.isEmpty()) return
    Text(
        text = artists,
        style = MaterialTheme.typography.bodyMedium,
        color = MuzzzloColors.TextSecondary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Shows the dialog of a step that replaced the actions; false for [MenuStep.Actions], which shows nothing here. */
@Composable
private fun StepDialog(
    step: MenuStep,
    artistChoices: List<ArtistChoice>,
    currentRating: Int?,
    onOpenArtist: ((Long) -> Unit)?,
    onRate: (Int?) -> Unit,
    addToPlaylist: AddToPlaylist?,
    onNewPlaylist: () -> Unit,
    onDismiss: () -> Unit,
): Boolean {
    when (step) {
        MenuStep.Actions -> return false
        MenuStep.Artists ->
            ArtistPickerMenu(artists = artistChoices, onPick = { onOpenArtist?.invoke(it.id) }, onDismiss = onDismiss)
        MenuStep.Rating -> RatingDialog(current = currentRating, onRate = onRate, onDismiss = onDismiss)
        MenuStep.Playlists, MenuStep.NewPlaylist ->
            addToPlaylist?.let { PlaylistSteps(it, step, onNew = onNewPlaylist, onDismiss) }
    }
    return true
}

/** The dialog that replaces the actions after [action], or null when the action is done in the menu itself. */
private fun stepAfter(
    action: TrackAction,
    tap: ArtistTap,
): MenuStep? =
    when {
        action == TrackAction.Rate -> MenuStep.Rating
        action == TrackAction.AddToPlaylist -> MenuStep.Playlists
        tap is ArtistTap.Choose -> MenuStep.Artists
        else -> null
    }

private val OPTIONAL_ACTIONS =
    setOf(
        TrackAction.GoToArtist,
        TrackAction.AddToPlaylist,
        TrackAction.AddToFavorites,
        TrackAction.Rate,
        TrackAction.Share,
    )

/** The actions of [OPTIONAL_ACTIONS] that the screen has given the menu what they need for. */
private fun optionalActions(
    canOpenArtist: Boolean,
    canAddToPlaylist: Boolean,
    canMark: Boolean,
    canShare: Boolean,
): Set<TrackAction> =
    buildSet {
        if (canOpenArtist) add(TrackAction.GoToArtist)
        if (canAddToPlaylist) add(TrackAction.AddToPlaylist)
        if (canMark) addAll(listOf(TrackAction.AddToFavorites, TrackAction.Rate))
        if (canShare) add(TrackAction.Share)
    }

@StringRes
private fun labelOf(
    action: TrackAction,
    isFavorite: Boolean,
): Int =
    if (action == TrackAction.AddToFavorites && isFavorite) {
        R.string.track_action_remove_from_favorites
    } else {
        action.label
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
