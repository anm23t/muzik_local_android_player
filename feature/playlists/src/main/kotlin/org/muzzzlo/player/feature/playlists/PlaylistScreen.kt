package org.muzzzlo.player.feature.playlists

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import org.muzzzlo.player.core.designsystem.gesture.rememberReorderState
import org.muzzzlo.player.core.designsystem.gesture.reorderHandle
import org.muzzzlo.player.core.designsystem.gesture.reorderableItem
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.model.Playlist
import org.muzzzlo.player.core.model.PlaylistEntry
import org.muzzzlo.player.core.designsystem.R as DesignR

/**
 * Playlist card. Not in the mockup: the album card (4:311) with the cover of the first track, the name, the number
 * of tracks and "Play"; track rows (4:337, 4:361) with covers as in "Songs" and a drag handle; the pencil button
 * (as on the album card) opens "Rename", "Export M3U8" and "Delete playlist" (DESIGN.md, section 7).
 *
 * @param onOpenArtist opens the card of the artist with this id (track menu).
 * @param miniPlayer the mini player of `feature:player`, passed in by the app.
 */
@Composable
fun PlaylistScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onEditTags: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    miniPlayer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf(PlaylistDialog.None) }
    var menuEntry by remember { mutableStateOf<PlaylistEntry?>(null) }
    var coverMenuOpen by rememberSaveable { mutableStateOf(false) }
    val playlist = state.playlist
    val coverPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { viewModel.onCoverPicked(it.toString()) }
        }
    val pickCover = { coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val exportFile =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PLAYLIST_EXPORT_TYPE)) { uri ->
            if (uri != null) viewModel.onExport(uri.toString())
        }
    state.message?.let { PlaylistFileDialog(it, viewModel::onMessageDismissed) }
    if (playlist != null) {
        PlaylistDialogs(
            dialog = dialog,
            playlist = playlist,
            onChange = { dialog = it },
            onRename = viewModel::onRename,
            onExport = { exportFile.launch("${playlist.name}.m3u8") },
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
                    TrackAction.EditTags -> onEditTags(entry.track.id)
                    else -> Unit
                }
            },
            onDismiss = { menuEntry = null },
            actions = TrackAction.Playlist,
            loadArtists = { viewModel.artistChoices(entry.track.id) },
            onOpenArtist = onOpenArtist,
            addToPlaylist = remember(entry.entryId) { viewModel.addToPlaylist(entry.track.id) },
            marks = remember(entry.entryId) { viewModel.trackMarks(entry.track.id) },
            shareUri = entry.track.uri,
            albumId = entry.track.albumId,
            onOpenAlbum = onOpenAlbum,
            trackId = entry.track.id,
        )
    }

    if (coverMenuOpen && playlist != null) {
        CoverMenu(
            playlistName = playlist.name,
            onPick = pickCover,
            onReset = viewModel::onCoverReset,
            onDismiss = { coverMenuOpen = false },
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
        PlaylistHeader(
            name = playlist.name,
            trackCount = playlist.trackCount,
            firstTrackId = playlist.firstTrackId,
            coverPath = playlist.coverPath,
            onCoverLongPress = { if (playlist.coverPath == null) pickCover() else coverMenuOpen = true },
            onManage = { dialog = PlaylistDialog.Actions },
            onPlay = viewModel::onPlay,
        )
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

/**
 * Shown on a long press on the cover when the playlist already has a picture of its own. Not in the mockup
 * (DESIGN.md, section 7).
 */
@Composable
private fun CoverMenu(
    playlistName: String,
    onPick: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    MuzzzloDialog(title = playlistName, onDismiss = onDismiss) {
        ActionRow(
            icon = DesignR.drawable.ic_pencil,
            text = stringResource(R.string.playlist_cover_choose),
            onClick = {
                onDismiss()
                onPick()
            },
        )
        ActionRow(
            icon = DesignR.drawable.ic_close,
            text = stringResource(R.string.playlist_cover_reset),
            onClick = {
                onDismiss()
                onReset()
            },
        )
    }
}

/**
 * Cover, name, number of tracks and "Play"; the pencil button is shown when there is something to [onManage].
 *
 * @param coverPath the picture the user picked; null shows the cover of the first track.
 * @param onCoverLongPress a long press on the cover (change it); null for a playlist whose cover cannot be changed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlaylistHeader(
    name: String,
    trackCount: Int,
    firstTrackId: Long?,
    onManage: (() -> Unit)?,
    onPlay: () -> Unit,
    coverPath: String? = null,
    onCoverLongPress: (() -> Unit)? = null,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.SectionGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val changeLabel = stringResource(R.string.playlist_cover_change)
        CoverCard(
            modifier =
                Modifier
                    .size(MuzzzloDimens.AlbumCardCover)
                    .clip(MuzzzloDimens.MiniPlayerShape)
                    .then(
                        if (onCoverLongPress == null) {
                            Modifier
                        } else {
                            Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = onCoverLongPress,
                                onLongClickLabel = changeLabel,
                            )
                        },
                    ),
            shape = MuzzzloDimens.MiniPlayerShape,
            coverShape = MuzzzloDimens.TileShape,
            inset = MuzzzloDimens.AlbumCardInset,
            cover = { PlaylistCover(firstTrackId, it, coverPath) },
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.SmallGap)) {
            Text(
                text = name,
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
            text = trackCountText(trackCount),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MuzzzloColors.TextSecondary,
        )
        if (onManage != null) {
            RoundIconButton(
                icon = DesignR.drawable.ic_pencil,
                contentDescription = stringResource(R.string.playlist_manage),
                onClick = onManage,
                size = RoundIconButtonSize.Small,
            )
            Spacer(Modifier.size(MuzzzloDimens.ItemGap))
        }
        if (trackCount > 0) {
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
    val entries = state.entries
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState, onMoved)
    reorder.keys = entries.map { it.entryId }
    reorder.stepPx = rowHeightPx + gapPx
    LazyColumn(
        state = listState,
        contentPadding = MiniPlayerClearance,
        verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.TinyGap),
    ) {
        itemsIndexed(entries, key = { _, entry -> entry.entryId }) { index, entry ->
            TrackRow(
                title = entry.track.title,
                ratingTenths = entry.track.ratingTenths,
                onClick = { onClick(index) },
                modifier =
                    reorderableItem(reorder, entry.entryId, index)
                        .onSizeChanged { rowHeightPx = it.height.toFloat() },
                subtitle = entry.track.artistsText.ifEmpty { null },
                cover = { PlaylistCover(entry.track.id, it) },
                isCurrent = entry.track.id == state.currentTrackId,
                onMoreClick = { onMore(entry) },
                // Dark rows look heavy on the colored live background, as on the album card.
                transparent = true,
                trailing = {
                    Icon(
                        painter = painterResource(DesignR.drawable.ic_grip_vertical),
                        contentDescription = stringResource(R.string.playlist_reorder),
                        modifier =
                            Modifier
                                .size(MuzzzloDimens.SmallIcon)
                                .reorderHandle(reorder, entry.entryId),
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
    onExport: () -> Unit,
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
                    icon = DesignR.drawable.ic_upload,
                    text = stringResource(R.string.playlist_export),
                    onClick = {
                        close()
                        onExport()
                    },
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
