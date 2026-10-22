package org.muzzzlo.player.feature.playlists

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.ActionRow
import org.muzzzlo.player.core.designsystem.component.CoverCard
import org.muzzzlo.player.core.designsystem.component.MuzzzloDialog
import org.muzzzlo.player.core.designsystem.component.PillButton
import org.muzzzlo.player.core.designsystem.component.PlaylistNameDialog
import org.muzzzlo.player.core.designsystem.component.RoundIconButton
import org.muzzzlo.player.core.designsystem.component.RoundIconButtonSize
import org.muzzzlo.player.core.designsystem.component.ScreenFrame
import org.muzzzlo.player.core.designsystem.component.TrackAction
import org.muzzzlo.player.core.designsystem.component.TrackActionsMenu
import org.muzzzlo.player.core.designsystem.component.TrackRow
import org.muzzzlo.player.core.designsystem.gesture.dragTargetIndex
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.model.Playlist
import org.muzzzlo.player.core.model.PlaylistEntry
import org.muzzzlo.player.core.designsystem.R as DesignR

/**
 * Playlist card. Not in the mockup: the album card (4:311) with the cover of the first track, the name, the number
 * of tracks and "Play"; track rows (4:337, 4:361) with covers as in "Songs" and a drag handle; the pencil button
 * (as on the album card) opens "Rename" and "Delete playlist" (DESIGN.md, section 7).
 *
 * @param onOpenArtist opens the card of the artist with this id (track menu).
 * @param miniPlayer the mini player of `feature:player`, passed in by the app.
 */
@Composable
fun PlaylistScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenQueue: () -> Unit,
    onEditTags: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    miniPlayer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf(PlaylistDialog.None) }
    var menuEntry by remember { mutableStateOf<PlaylistEntry?>(null) }
    val playlist = state.playlist
    if (playlist != null) {
        PlaylistDialogs(
            dialog = dialog,
            playlist = playlist,
            onChange = { dialog = it },
            onRename = viewModel::onRename,
            onDelete = {
                viewModel.onDelete()
                onBack()
            },
        )
    }
    menuEntry?.let { entry ->
        TrackActionsMenu(
            title = entry.track.title,
            artists = entry.track.artistsText,
            onAction = { action ->
                viewModel.onTrackAction(entry, action)
                when (action) {
                    TrackAction.OpenQueue -> onOpenQueue()
                    TrackAction.EditTags -> onEditTags(entry.track.id)
                    else -> Unit
                }
            },
            onDismiss = { menuEntry = null },
            actions = TrackAction.entries,
            loadArtists = { viewModel.artistChoices(entry.track.id) },
            onOpenArtist = onOpenArtist,
            addToPlaylist = remember(entry.entryId) { viewModel.addToPlaylist(entry.track.id) },
            marks = remember(entry.entryId) { viewModel.trackMarks(entry.track.id) },
        )
    }

    ScreenFrame(modifier) {
        ScreenHeader(onBack) { HomeButton(onHome) }
        if (playlist == null) {
            if (state.isLoaded) {
                ContentWithMiniPlayer(miniPlayer) { Notice(stringResource(R.string.playlist_not_found)) }
            }
            return@ScreenFrame
        }
        PlaylistHeader(playlist, onManage = { dialog = PlaylistDialog.Actions }, onPlay = viewModel::onPlay)
        ContentWithMiniPlayer(miniPlayer) {
            if (state.entries.isEmpty()) {
                Notice(stringResource(R.string.playlist_empty))
            } else {
                PlaylistTracks(
                    state = state,
                    onClick = viewModel::onTrackClicked,
                    onMore = { menuEntry = it },
                    onMoved = viewModel::onMoved,
                )
            }
        }
    }
}

@Composable
private fun PlaylistHeader(
    playlist: Playlist,
    onManage: () -> Unit,
    onPlay: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.SectionGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverCard(
            modifier = Modifier.size(MuzzzloDimens.AlbumCardCover),
            shape = MuzzzloDimens.MiniPlayerShape,
            coverShape = MuzzzloDimens.TileShape,
            inset = MuzzzloDimens.AlbumCardInset,
            cover = { PlaylistCover(playlist.firstTrackId, it) },
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.SmallGap)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = MuzzzloColors.TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(DesignR.drawable.ic_list_music),
            contentDescription = null,
            modifier = Modifier.size(MuzzzloDimens.SmallIcon),
            tint = MuzzzloColors.TextSecondary,
        )
        Spacer(Modifier.size(MuzzzloDimens.SmallGap))
        Text(
            text = trackCountText(playlist.trackCount),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MuzzzloColors.TextSecondary,
        )
        RoundIconButton(
            icon = DesignR.drawable.ic_pencil,
            contentDescription = stringResource(R.string.playlist_manage),
            onClick = onManage,
            size = RoundIconButtonSize.Small,
        )
        if (playlist.trackCount > 0) {
            Spacer(Modifier.size(MuzzzloDimens.ItemGap))
            PillButton(text = stringResource(R.string.playlist_play), onClick = onPlay, icon = DesignR.drawable.ic_play)
        }
    }
}

/** Tracks with their covers; the handle on the right drags a row to a new place. */
@Composable
private fun PlaylistTracks(
    state: PlaylistUiState,
    onClick: (Int) -> Unit,
    onMore: (PlaylistEntry) -> Unit,
    onMoved: (Int, Int) -> Unit,
) {
    val gapPx = with(LocalDensity.current) { MuzzzloDimens.TinyGap.toPx() }
    var rowHeightPx by remember { mutableFloatStateOf(0f) }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val entries = state.entries
    LazyColumn(
        contentPadding = MiniPlayerClearance,
        verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.TinyGap),
    ) {
        itemsIndexed(entries, key = { _, entry -> entry.entryId }) { index, entry ->
            val isDragged = index == draggingIndex
            TrackRow(
                title = entry.track.title,
                onClick = { onClick(index) },
                modifier =
                    Modifier
                        .onSizeChanged { rowHeightPx = it.height.toFloat() }
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragged) dragOffset else 0f },
                subtitle = entry.track.artistsText.ifEmpty { null },
                cover = { PlaylistCover(entry.track.id, it) },
                isCurrent = entry.track.id == state.currentTrackId,
                onMoreClick = { onMore(entry) },
                trailing = {
                    Icon(
                        painter = painterResource(DesignR.drawable.ic_grip_vertical),
                        contentDescription = stringResource(R.string.playlist_reorder),
                        modifier =
                            Modifier
                                .size(MuzzzloDimens.SmallIcon)
                                .pointerInput(index, entries.size) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingIndex = index
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                        },
                                        onDragEnd = {
                                            val step = rowHeightPx + gapPx
                                            val target = dragTargetIndex(index, dragOffset, step, entries.size)
                                            if (target != index) onMoved(index, target)
                                            draggingIndex = -1
                                            dragOffset = 0f
                                        },
                                        onDragCancel = {
                                            draggingIndex = -1
                                            dragOffset = 0f
                                        },
                                    )
                                },
                        tint = MuzzzloColors.TextSecondary,
                    )
                },
            )
        }
    }
}

/** Dialogs of the pencil button. */
private enum class PlaylistDialog { None, Actions, Rename, Delete }

@Composable
private fun PlaylistDialogs(
    dialog: PlaylistDialog,
    playlist: Playlist,
    onChange: (PlaylistDialog) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val close = { onChange(PlaylistDialog.None) }
    when (dialog) {
        PlaylistDialog.None -> Unit
        PlaylistDialog.Actions ->
            MuzzzloDialog(title = playlist.name, onDismiss = close) {
                ActionRow(
                    icon = DesignR.drawable.ic_pencil,
                    text = stringResource(R.string.playlist_rename),
                    onClick = { onChange(PlaylistDialog.Rename) },
                )
                ActionRow(
                    icon = DesignR.drawable.ic_trash,
                    text = stringResource(R.string.playlist_delete),
                    onClick = { onChange(PlaylistDialog.Delete) },
                )
            }
        PlaylistDialog.Rename ->
            PlaylistNameDialog(
                title = stringResource(R.string.playlist_rename_title),
                confirmLabel = stringResource(R.string.playlist_save),
                onConfirm = onRename,
                onDismiss = close,
                initialName = playlist.name,
            )
        PlaylistDialog.Delete ->
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
