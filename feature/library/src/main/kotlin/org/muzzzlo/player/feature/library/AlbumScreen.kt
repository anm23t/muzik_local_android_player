package org.muzzzlo.player.feature.library

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.ArtistPhotoPlaceholder
import org.muzzzlo.player.core.designsystem.component.CoverCard
import org.muzzzlo.player.core.designsystem.component.PillButton
import org.muzzzlo.player.core.designsystem.component.ScreenFrame
import org.muzzzlo.player.core.designsystem.component.TrackRow
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.model.Album
import org.muzzzlo.player.core.model.Track
import java.util.Locale
import org.muzzzlo.player.core.designsystem.R as DesignR

/**
 * Album card (4:311): cover, title, album artist, year, number of tracks, "Play" and the tracks.
 * A track row shows its own artists only when they differ from the album artists.
 *
 * @param miniPlayer the mini player of `feature:player`, passed in by the app.
 */
@Composable
fun AlbumScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenQueue: () -> Unit,
    miniPlayer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openMenu = rememberTrackMenu(viewModel::onTrackAction, onOpenQueue)

    ScreenFrame(modifier) {
        ScreenHeader(onBack = onBack) { HomeButton(onHome) }
        val album = state.album ?: return@ScreenFrame
        AlbumHeader(album)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(DesignR.drawable.ic_folder_check),
                contentDescription = null,
                modifier = Modifier.size(MuzzzloDimens.SmallIcon),
                tint = MuzzzloColors.TextSecondary,
            )
            Spacer(Modifier.size(MuzzzloDimens.SmallGap))
            Text(
                text = trackCountText(album.trackCount),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MuzzzloColors.TextSecondary,
            )
            PillButton(
                text = stringResource(R.string.album_play),
                onClick = viewModel::onPlayAlbum,
                icon = DesignR.drawable.ic_play,
            )
        }
        ContentWithMiniPlayer(miniPlayer) {
            LazyColumn(
                contentPadding = MiniPlayerClearance,
                verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.TinyGap),
            ) {
                itemsIndexed(state.tracks, key = { _, track -> track.id }) { index, track ->
                    TrackRow(
                        title = track.title,
                        onClick = { viewModel.onTrackClicked(index) },
                        number = trackNumberText(track, index),
                        subtitle = track.artistsText.takeIf { track.artists != album.artists && it.isNotEmpty() },
                        isCurrent = track.id == state.currentTrackId,
                        onMoreClick = { openMenu(track) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumHeader(album: Album) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.SectionGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverCard(
            modifier = Modifier.size(MuzzzloDimens.AlbumCardCover),
            shape = MuzzzloDimens.MiniPlayerShape,
            coverShape = MuzzzloDimens.TileShape,
            inset = MuzzzloDimens.AlbumCardInset,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.SmallGap)) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleMedium,
                color = MuzzzloColors.TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            // The artist card opens from here in 0.3.5b.
            Row(
                horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.ItemGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtistPhotoPlaceholder(MuzzzloDimens.InlineArtistPhoto)
                Text(
                    text = albumSubtitle(album),
                    style = MaterialTheme.typography.titleSmall,
                    color = MuzzzloColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            album.year?.let { year ->
                Text(
                    text = year.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MuzzzloColors.TextSecondary,
                )
            }
        }
    }
}

/** Two-digit track number as in the mockup ("01"); the position stands in when the tag is missing. */
private fun trackNumberText(
    track: Track,
    index: Int,
): String = String.format(Locale.ROOT, "%02d", track.trackNumber ?: (index + 1))
