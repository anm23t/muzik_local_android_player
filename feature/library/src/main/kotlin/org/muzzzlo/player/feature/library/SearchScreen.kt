package org.muzzzlo.player.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.AlbumRow
import org.muzzzlo.player.core.designsystem.component.ArtistPhoto
import org.muzzzlo.player.core.designsystem.component.ArtistRow
import org.muzzzlo.player.core.designsystem.component.CoverImage
import org.muzzzlo.player.core.designsystem.component.CoverSource
import org.muzzzlo.player.core.designsystem.component.PillButton
import org.muzzzlo.player.core.designsystem.component.ScreenFrame
import org.muzzzlo.player.core.designsystem.component.SearchField
import org.muzzzlo.player.core.designsystem.component.TrackRow
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.model.Album
import org.muzzzlo.player.core.model.Track

/** Rows of a section before "Show all". */
private const val SECTION_PREVIEW = 5

/**
 * Search (task 1.2.4), opened by the magnifier on the home screen. Not in the mockup (DESIGN.md, section 7): the
 * frame and header of the lists, the search field, then "Songs", "Albums", "Artists" and "Playlists" with the rows of
 * their own lists, up to five each and "Show all" under a longer section.
 *
 * @param miniPlayer the mini player of `feature:player`, passed in by the app.
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenQueue: () -> Unit,
    onEditTags: (Long) -> Unit,
    miniPlayer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openMenu =
        rememberTrackMenu(
            viewModel::onTrackAction,
            onOpenQueue,
            onEditTags,
            viewModel::artistChoices,
            onOpenArtist,
            viewModel::addToPlaylist,
            viewModel::trackMarks,
        )

    ScreenFrame(modifier) {
        ScreenHeader(onBack = onBack)
        SearchField(
            value = state.query,
            onValueChange = viewModel::onQueryChanged,
            placeholder = stringResource(R.string.search_placeholder),
            autoFocus = state.isBlank,
        )
        ContentWithMiniPlayer(miniPlayer) {
            when {
                state.isBlank -> Notice(stringResource(R.string.search_hint))
                state.nothingFound -> Notice(stringResource(R.string.search_nothing_found))
                else ->
                    SearchResultsList(
                        state = state,
                        onTrackClick = viewModel::onTrackClicked,
                        onTrackMenu = openMenu,
                        onOpenAlbum = onOpenAlbum,
                        onOpenArtist = onOpenArtist,
                        onOpenPlaylist = onOpenPlaylist,
                    )
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    state: SearchUiState,
    onTrackClick: (Int) -> Unit,
    onTrackMenu: (Track) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
) {
    val results = state.results
    // Opened sections close again with a new query.
    var expanded by rememberSaveable(state.resultsFor) { mutableStateOf(emptySet<Int>()) }
    val unknownArtist = stringResource(R.string.unknown_artist)
    val labels =
        listOf(
            stringResource(R.string.search_songs),
            stringResource(R.string.albums_title),
            stringResource(R.string.artists_title),
            stringResource(R.string.nav_playlists),
        )
    val showAll = stringResource(R.string.search_show_all)
    val albumSubtitles = results.albums.associate { it.id to artistsLine(it, unknownArtist) }
    val playlistCounts = results.playlists.associate { it.id to trackCountText(it.trackCount) }

    LazyColumn(
        contentPadding = MiniPlayerClearance,
        verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.TinyGap),
    ) {
        section(SECTION_SONGS, labels[SECTION_SONGS], results.tracks, expanded, showAll, { expanded += it }) {
                index,
                track,
            ->
            TrackRow(
                title = track.title,
                ratingTenths = track.ratingTenths,
                onClick = { onTrackClick(index) },
                subtitle = track.artistsText.ifEmpty { unknownArtist },
                cover = { CoverImage(CoverSource.Track(track.id), it) },
                isCurrent = track.id == state.currentTrackId,
                onMoreClick = { onTrackMenu(track) },
            )
        }
        section(SECTION_ALBUMS, labels[SECTION_ALBUMS], results.albums, expanded, showAll, { expanded += it }) {
                _,
                album,
            ->
            AlbumRow(
                title = album.title,
                subtitle = albumSubtitles.getValue(album.id),
                onClick = { onOpenAlbum(album.id) },
                cover = { CoverImage(CoverSource.Album(album.id), it) },
            )
        }
        section(SECTION_ARTISTS, labels[SECTION_ARTISTS], results.artists, expanded, showAll, { expanded += it }) {
                _,
                artist,
            ->
            ArtistRow(
                name = artist.name,
                onClick = { onOpenArtist(artist.id) },
                photo =
                    artist.photoPath?.let { path ->
                        @Composable { photoModifier: Modifier ->
                            ArtistPhoto(path, MuzzzloDimens.ArtistPhoto, photoModifier)
                        }
                    },
            )
        }
        section(
            SECTION_PLAYLISTS,
            labels[SECTION_PLAYLISTS],
            results.playlists,
            expanded,
            showAll,
            { expanded += it },
        ) { _, playlist ->
            AlbumRow(
                title = playlist.name,
                subtitle = playlistCounts.getValue(playlist.id),
                onClick = { onOpenPlaylist(playlist.id) },
                cover = { CoverImage(playlist.firstTrackId?.let { id -> CoverSource.Track(id) }, it) },
            )
        }
    }
}

private const val SECTION_SONGS = 0
private const val SECTION_ALBUMS = 1
private const val SECTION_ARTISTS = 2
private const val SECTION_PLAYLISTS = 3

/** A titled block of results: the first [SECTION_PREVIEW] rows, all of them once "Show all" is pressed. */
@Suppress("LongParameterList")
private fun <T> LazyListScope.section(
    id: Int,
    title: String,
    items: List<T>,
    expanded: Set<Int>,
    showAllLabel: String,
    onShowAll: (Int) -> Unit,
    row: @Composable (index: Int, item: T) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "title-$id") { ResultsTitle(title, items.size) }
    val shown = if (id in expanded) items else items.take(SECTION_PREVIEW)
    shown.forEachIndexed { index, value -> item(key = "row-$id-$index") { row(index, value) } }
    if (shown.size < items.size) {
        item(key = "more-$id") {
            Row(Modifier.fillMaxWidth().padding(vertical = MuzzzloDimens.SmallGap)) {
                PillButton(text = showAllLabel, onClick = { onShowAll(id) })
            }
        }
    }
}

@Composable
private fun ResultsTitle(
    title: String,
    count: Int,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = MuzzzloDimens.ContentGap, bottom = MuzzzloDimens.SmallGap),
        horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.ItemGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            color = MuzzzloColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MuzzzloColors.TextSecondary,
        )
    }
}

private fun artistsLine(
    album: Album,
    unknownArtist: String,
): String = album.artistsText.ifEmpty { unknownArtist }
