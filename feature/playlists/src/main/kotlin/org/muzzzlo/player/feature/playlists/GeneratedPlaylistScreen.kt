package org.muzzzlo.player.feature.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.MuzzzloDialog
import org.muzzzlo.player.core.designsystem.component.ScreenFrame
import org.muzzzlo.player.core.designsystem.component.TrackAction
import org.muzzzlo.player.core.designsystem.component.TrackActionsMenu
import org.muzzzlo.player.core.designsystem.component.TrackRow
import org.muzzzlo.player.core.designsystem.component.seasonName
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.model.CalendarPeriod
import org.muzzzlo.player.core.model.GeneratedKind
import org.muzzzlo.player.core.model.GeneratedPlaylist
import org.muzzzlo.player.core.model.Track
import java.time.LocalDate
import org.muzzzlo.player.core.designsystem.R as DesignR

/**
 * Card of a generated playlist (task 2.5.7). Not in the mockup: the playlist card (4:311, 4:337, 4:361) with the
 * gradient cover of the set, without the pencil and the drag handles, and with "Add" next to "Play"
 * (DESIGN.md, section 7).
 *
 * @param onOpenArtist opens the card of the artist with this id (track menu).
 * @param miniPlayer the mini player of `feature:player`, passed in by the app.
 */
@Composable
fun GeneratedPlaylistScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onEditTags: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    miniPlayer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GeneratedPlaylistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuTrack by remember { mutableStateOf<Track?>(null) }
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
    state.savedAs?.let { SavedDialog(it, viewModel::onSavedMessageDismissed) }

    ScreenFrame(modifier) {
        ScreenHeader(onBack) { HomeButton(onHome) }
        val kind = state.kind
        val playlist = state.playlist
        if (kind == null || playlist == null || !playlist.isShown) {
            if (state.isLoaded) {
                ContentWithMiniPlayer(miniPlayer) { Notice(stringResource(R.string.playlist_not_found)) }
            }
            return@ScreenFrame
        }
        val name = generatedName(playlist)
        PlaylistHeader(
            name = name,
            trackCount = state.tracks.size,
            firstTrackId = state.tracks.firstOrNull()?.id,
            onManage = null,
            onPlay = viewModel::onPlay,
            gradient = playlist.gradient,
            onAdd = { viewModel.onAdd(name) },
        )
        ContentWithMiniPlayer(miniPlayer) {
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
                        // Dark rows look heavy on the colored live background, as on the album card.
                        transparent = true,
                    )
                }
            }
        }
    }
}

/** Says under which name "Add" kept the playlist. */
@Composable
private fun SavedDialog(
    name: String,
    onDismiss: () -> Unit,
) {
    MuzzzloDialog(stringResource(R.string.generated_added_title), onDismiss) {
        Text(
            text = stringResource(R.string.generated_added, name),
            style = MaterialTheme.typography.bodyLarge,
            color = MuzzzloColors.TextPrimary,
        )
    }
}

/** The name of a generated playlist; the same as on the home screen. */
@Composable
internal fun generatedName(playlist: GeneratedPlaylist): String =
    when (playlist.kind) {
        GeneratedKind.DAILY_MIX -> stringResource(DesignR.string.generated_daily_mix)
        GeneratedKind.ON_REPEAT -> stringResource(DesignR.string.generated_on_repeat)
        GeneratedKind.FORGOTTEN_FAVORITES -> stringResource(DesignR.string.generated_forgotten_favorites)
        GeneratedKind.BEST_RATED -> stringResource(DesignR.string.generated_best_rated)
        GeneratedKind.RECENTLY_ADDED -> stringResource(DesignR.string.generated_recently_added)
        GeneratedKind.MOST_PLAYED_SEASON, GeneratedKind.MOST_PLAYED_YEAR ->
            when (val period = playlist.period) {
                is CalendarPeriod.SeasonOf ->
                    stringResource(
                        DesignR.string.generated_most_played_season,
                        seasonName(period.season.ordinal, period.year),
                    )
                is CalendarPeriod.Year -> stringResource(DesignR.string.generated_most_played_year, period.year)
                else ->
                    stringResource(
                        DesignR.string.generated_most_played_year,
                        LocalDate.ofEpochDay(playlist.epochDay).year,
                    )
            }
    }
