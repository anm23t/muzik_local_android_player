package org.muzzzlo.player.feature.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.ActionRow
import org.muzzzlo.player.core.designsystem.component.MuzzzloDialog
import org.muzzzlo.player.core.designsystem.component.PillButton
import org.muzzzlo.player.core.designsystem.component.ScreenFrame
import org.muzzzlo.player.core.designsystem.component.TrackAction
import org.muzzzlo.player.core.designsystem.component.TrackActionsMenu
import org.muzzzlo.player.core.designsystem.component.TrackRow
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.model.SmartPlaylist
import org.muzzzlo.player.core.model.Track
import org.muzzzlo.player.core.designsystem.R as DesignR

/**
 * Card of a smart playlist (task 2.5.10). Not in the mockup: the card of an automatic playlist (4:311, 4:337, 4:361)
 * with the gradient cover of the playlist and the pencil button, whose menu has "Edit rules" and "Delete playlist"
 * (DESIGN.md, section 7). The list fills itself by the rules, so there are no drag handles.
 *
 * @param onEditRules opens the rules editor of this playlist.
 * @param miniPlayer the mini player of `feature:player`, passed in by the app.
 */
@Composable
fun SmartPlaylistScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onEditRules: (Long) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onEditTags: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    miniPlayer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SmartPlaylistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var dialog by rememberSaveable { mutableStateOf(SmartDialog.None) }
    LaunchedEffect(state.isDeleted) { if (state.isDeleted) onBack() }
    menuTrack?.let { track ->
        TrackActionsMenu(
            title = track.title,
            artists = track.artistsText,
            onAction = { action ->
                viewModel.onTrackAction(track, action)
                if (action == TrackAction.EditTags) onEditTags(track.id)
            },
            onDismiss = { menuTrack = null },
            loadArtists = { viewModel.artistChoices(track.id) },
            onOpenArtist = onOpenArtist,
            addToPlaylist = remember(track.id) { viewModel.addToPlaylist(track.id) },
            marks = remember(track.id) { viewModel.trackMarks(track.id) },
            shareUri = track.uri,
            albumId = track.albumId,
            onOpenAlbum = onOpenAlbum,
            trackId = track.id,
        )
    }

    ScreenFrame(modifier) {
        ScreenHeader(onBack) { HomeButton(onHome) }
        val playlist = state.playlist
        if (playlist == null) {
            if (state.isLoaded && !state.isDeleted) {
                ContentWithMiniPlayer(miniPlayer) { Notice(stringResource(R.string.playlist_not_found)) }
            }
            return@ScreenFrame
        }
        SmartDialogs(
            dialog = dialog,
            playlist = playlist,
            onChange = { dialog = it },
            onEditRules = { onEditRules(playlist.id) },
            onDelete = viewModel::onDelete,
        )
        PlaylistHeader(
            name = playlist.name,
            trackCount = state.tracks.size,
            firstTrackId = state.tracks.firstOrNull()?.id,
            onManage = { dialog = SmartDialog.Actions },
            onPlay = viewModel::onPlay,
            gradient = state.gradient,
        )
        ContentWithMiniPlayer(miniPlayer) {
            if (state.tracks.isEmpty()) {
                Notice(stringResource(R.string.smart_playlist_empty))
            } else {
                LazyColumn(
                    contentPadding = MiniPlayerClearance,
                    verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.TinyGap),
                ) {
                    itemsIndexed(state.tracks, key = { _, track -> track.id }) { index, track ->
                        TrackRow(
                            title = track.title,
                            ratingTenths = track.ratingTenths,
                            onClick = { viewModel.onTrackClicked(index) },
                            subtitle = track.artistsText.ifEmpty { null },
                            cover = { PlaylistCover(track.id, it) },
                            isCurrent = track.id == state.currentTrackId,
                            onMoreClick = { menuTrack = track },
                            transparent = true,
                        )
                    }
                }
            }
        }
    }
}

/** Dialogs of the pencil button of a smart playlist. */
private enum class SmartDialog { None, Actions, Delete }

@Composable
private fun SmartDialogs(
    dialog: SmartDialog,
    playlist: SmartPlaylist,
    onChange: (SmartDialog) -> Unit,
    onEditRules: () -> Unit,
    onDelete: () -> Unit,
) {
    val close = { onChange(SmartDialog.None) }
    when (dialog) {
        SmartDialog.None -> Unit
        SmartDialog.Actions ->
            MuzzzloDialog(title = playlist.name, onDismiss = close) {
                ActionRow(
                    icon = DesignR.drawable.ic_pencil,
                    text = stringResource(R.string.smart_playlist_edit_rules),
                    onClick = {
                        close()
                        onEditRules()
                    },
                )
                ActionRow(
                    icon = DesignR.drawable.ic_trash,
                    text = stringResource(R.string.playlist_delete),
                    onClick = { onChange(SmartDialog.Delete) },
                )
            }
        SmartDialog.Delete ->
            MuzzzloDialog(title = stringResource(R.string.playlist_delete), onDismiss = close) {
                Text(
                    text = stringResource(R.string.playlist_delete_text, playlist.name),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MuzzzloColors.TextPrimary,
                )
                PillButton(
                    text = stringResource(R.string.playlist_delete_confirm),
                    onClick = {
                        close()
                        onDelete()
                    },
                    wide = true,
                )
            }
    }
}
