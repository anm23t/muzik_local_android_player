package org.muzzzlo.player.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.ScreenFrame
import org.muzzzlo.player.core.designsystem.component.SectionTitle
import org.muzzzlo.player.core.designsystem.component.TrackRow
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens

/**
 * All tracks of the library, or of one artist when opened from the artist card. Not in the mockup
 * (DESIGN.md, section 7): the frame and title of the album list (15:3) with the track rows of the album card
 * (4:337, 4:361), artists under the title.
 *
 * @param miniPlayer the mini player of `feature:player`, passed in by the app.
 */
@Composable
fun TracksScreen(
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    miniPlayer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TracksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openMenu = rememberTrackMenu(viewModel::onTrackAction, onOpenQueue)
    val unknownArtist = stringResource(R.string.unknown_artist)

    ScreenFrame(modifier) {
        ScreenHeader(onBack = onBack)
        SectionTitle(state.artistName ?: stringResource(R.string.tracks_title))
        ContentWithMiniPlayer(miniPlayer) {
            if (state.isLoaded && state.tracks.isEmpty()) {
                Notice(stringResource(R.string.library_empty))
            } else {
                LazyColumn(
                    contentPadding = MiniPlayerClearance,
                    verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.TinyGap),
                ) {
                    itemsIndexed(state.tracks, key = { _, track -> track.id }) { index, track ->
                        TrackRow(
                            title = track.title,
                            onClick = { viewModel.onTrackClicked(index) },
                            subtitle = track.artistsText.ifEmpty { unknownArtist },
                            isCurrent = track.id == state.currentTrackId,
                            onMoreClick = { openMenu(track) },
                        )
                    }
                }
            }
        }
    }
}
