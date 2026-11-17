package org.muzzzlo.player.feature.playlists

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import org.muzzzlo.player.core.designsystem.component.MuzzzloDialog
import org.muzzzlo.player.core.designsystem.component.PlaylistNameDialog
import org.muzzzlo.player.core.designsystem.component.RadioOptionRow
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
 * "Favorites" and "Top songs" are the first two rows, built like the others, then the smart playlists with their
 * gradient covers (task 2.5.10). The plus asks what to create: a playlist or a smart playlist. The download button
 * left of the plus imports an M3U or M3U8 file chosen with the system picker (task 1.2.5).
 *
 * @param onOpenSmart opens the card of the smart playlist with this id.
 * @param onNewSmart opens the rules editor for a new smart playlist.
 * @param miniPlayer the mini player of `feature:player`, passed in by the app.
 */
@Composable
fun PlaylistListScreen(
    onBack: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenAutoPlaylist: (AutoPlaylist) -> Unit,
    onOpenSmart: (Long) -> Unit,
    onNewSmart: () -> Unit,
    miniPlayer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    var choosingKind by rememberSaveable { mutableStateOf(false) }
    val fallbackName = stringResource(R.string.playlist_import_fallback_name)
    val importFile =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.onImport(uri.toString(), fallbackName)
        }
    state.message?.let { PlaylistFileDialog(it, viewModel::onMessageDismissed, onOpenPlaylist) }
    if (choosingKind) {
        CreateKindDialog(
            onDismiss = { choosingKind = false },
            onPlain = { creating = true },
            onSmart = onNewSmart,
        )
    }
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
                icon = DesignR.drawable.ic_download,
                contentDescription = stringResource(R.string.playlist_import),
                onClick = { if (!state.busy) importFile.launch(PLAYLIST_FILE_TYPES) },
            )
            RoundIconButton(
                icon = DesignR.drawable.ic_plus,
                contentDescription = stringResource(DesignR.string.playlist_new),
                onClick = { choosingKind = true },
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
                items(state.smart, key = { "smart-${it.id}" }) { smart ->
                    AlbumRow(
                        title = smart.name,
                        subtitle = trackCountText(smart.trackCount),
                        onClick = { onOpenSmart(smart.id) },
                        cover = { PlaylistCover(null, it, gradient = smart.gradient) },
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
                            cover = {
                                PlaylistCover(
                                    playlist.firstTrackId,
                                    it,
                                    playlist.coverPath,
                                    playlist.coverGradient,
                                )
                            },
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

/** What the plus creates: a playlist filled by hand or a smart playlist filled by rules (task 2.5.10). */
@Composable
private fun CreateKindDialog(
    onDismiss: () -> Unit,
    onPlain: () -> Unit,
    onSmart: () -> Unit,
) {
    MuzzzloDialog(title = stringResource(R.string.smart_create_title), onDismiss = onDismiss) {
        RadioOptionRow(
            text = stringResource(R.string.smart_create_plain),
            selected = false,
            onClick = {
                onDismiss()
                onPlain()
            },
            secondary = stringResource(R.string.smart_create_plain_hint),
        )
        RadioOptionRow(
            text = stringResource(R.string.smart_create_smart),
            selected = false,
            onClick = {
                onDismiss()
                onSmart()
            },
            secondary = stringResource(R.string.smart_create_smart_hint),
        )
    }
}
