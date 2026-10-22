package org.muzzzlo.player.feature.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.AlbumRow
import org.muzzzlo.player.core.designsystem.component.PlaylistNameDialog
import org.muzzzlo.player.core.designsystem.component.RoundIconButton
import org.muzzzlo.player.core.designsystem.component.ScreenFrame
import org.muzzzlo.player.core.designsystem.component.SectionTitle
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.model.AutoPlaylist
import org.muzzzlo.player.core.designsystem.R as DesignR

/**
 * List of playlists. Not in the mockup: the album list (15:3, 25:555) with the playlist name, its number of tracks
 * and the cover of its first track; the plus button in the header (15:114) creates a playlist (DESIGN.md, section 7).
 * "Favorites" and "Top songs" are the first two rows, built like the others.
 *
 * @param miniPlayer the mini player of `feature:player`, passed in by the app.
 */
@Composable
fun PlaylistListScreen(
    onBack: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenAutoPlaylist: (AutoPlaylist) -> Unit,
    miniPlayer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    if (creating) {
        PlaylistNameDialog(
            title = stringResource(DesignR.string.playlist_new),
            confirmLabel = stringResource(DesignR.string.playlist_create),
            onConfirm = viewModel::onCreate,
            onDismiss = { creating = false },
        )
    }
    ScreenFrame(modifier) {
        ScreenHeader(onBack) {
            RoundIconButton(
                icon = DesignR.drawable.ic_plus,
                contentDescription = stringResource(DesignR.string.playlist_new),
                onClick = { creating = true },
            )
        }
        SectionTitle(stringResource(R.string.playlists_title))
        ContentWithMiniPlayer(miniPlayer) {
            LazyColumn(
                contentPadding = MiniPlayerClearance,
                verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.TinyGap),
            ) {
                items(state.auto, key = { it.playlist.key }) { auto ->
                    AlbumRow(
                        title = auto.playlist.title(),
                        subtitle = trackCountText(auto.trackCount),
                        onClick = { onOpenAutoPlaylist(auto.playlist) },
                        cover = { PlaylistCover(auto.firstTrackId, it) },
                    )
                }
                if (state.isLoaded && state.playlists.isEmpty()) {
                    item(key = "empty") { EmptyPlaylistsHint() }
                } else {
                    items(state.playlists, key = { it.id }) { playlist ->
                        AlbumRow(
                            title = playlist.name,
                            subtitle = trackCountText(playlist.trackCount),
                            onClick = { onOpenPlaylist(playlist.id) },
                            cover = { PlaylistCover(playlist.firstTrackId, it) },
                        )
                    }
                }
            }
        }
    }
}

/** Shown under the automatic playlists while the user has none of their own. */
@Composable
private fun EmptyPlaylistsHint() {
    Text(
        text = stringResource(R.string.playlists_empty),
        modifier = Modifier.padding(vertical = MuzzzloDimens.SectionGap),
        style = MaterialTheme.typography.bodyLarge,
        color = MuzzzloColors.TextSecondary,
        textAlign = TextAlign.Center,
    )
}
