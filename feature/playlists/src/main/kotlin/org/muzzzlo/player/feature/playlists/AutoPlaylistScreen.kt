package org.muzzzlo.player.feature.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.ScreenFrame
import org.muzzzlo.player.core.designsystem.component.TrackAction
import org.muzzzlo.player.core.designsystem.component.TrackActionsMenu
import org.muzzzlo.player.core.designsystem.component.TrackRow
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.model.Track

/**
 * Card of "Favorites" or "Top songs". Not in the mockup: the playlist card (4:311, 4:337, 4:361) without the pencil
 * button and the drag handles, because the list fills itself and cannot be edited (DESIGN.md, section 7).
 *
 * @param onOpenArtist opens the card of the artist with this id (track menu).
 * @param miniPlayer the mini player of `feature:player`, passed in by the app.
 */
@Composable
fun AutoPlaylistScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenQueue: () -> Unit,
    onEditTags: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    miniPlayer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AutoPlaylistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    menuTrack?.let { track ->
        TrackActionsMenu(
            title = track.title,
            artists = track.artistsText,
            onAction = { action ->
                viewModel.onTrackAction(track, action)
                when (action) {
                    TrackAction.OpenQueue -> onOpenQueue()
                    TrackAction.EditTags -> onEditTags(track.id)
                    else -> Unit
                }
            },
            onDismiss = { menuTrack = null },
            loadArtists = { viewModel.artistChoices(track.id) },
            onOpenArtist = onOpenArtist,
            addToPlaylist = remember(track.id) { viewModel.addToPlaylist(track.id) },
            marks = remember(track.id) { viewModel.trackMarks(track.id) },
        )
    }

    ScreenFrame(modifier) {
        ScreenHeader(onBack) { HomeButton(onHome) }
        val playlist = state.playlist
        if (playlist == null) {
            if (state.isLoaded) {
                ContentWithMiniPlayer(miniPlayer) { Notice(stringResource(R.string.playlist_not_found)) }
            }
            return@ScreenFrame
        }
        PlaylistHeader(
            name = playlist.title(),
            trackCount = state.tracks.size,
            firstTrackId = state.tracks.firstOrNull()?.id,
            onManage = null,
            onPlay = viewModel::onPlay,
        )
        ContentWithMiniPlayer(miniPlayer) {
            if (state.tracks.isEmpty()) {
                Notice(playlist.emptyText())
            } else {
                LazyColumn(
                    contentPadding = MiniPlayerClearance,
                    verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.TinyGap),
                ) {
                    itemsIndexed(state.tracks, key = { _, track -> track.id }) { index, track ->
                        TrackRow(
                            title = track.title,
                            onClick = { viewModel.onTrackClicked(index) },
                            subtitle = track.artistsText.ifEmpty { null },
                            cover = { PlaylistCover(track.id, it) },
                            isCurrent = track.id == state.currentTrackId,
                            onMoreClick = { menuTrack = track },
                        )
                    }
                }
            }
        }
    }
}
