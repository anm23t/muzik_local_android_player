package org.muzzzlo.player.feature.library

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.AlbumRow
import org.muzzzlo.player.core.model.Album

/**
 * Album list (15:3). The list scrolls under the mini player.
 *
 * @param miniPlayer the mini player of `feature:player`, passed in by the app.
 */
@Composable
fun AlbumListScreen(
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    miniPlayer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SortableListScreen(
        title = R.string.albums_title,
        sort = state.sort,
        fields = AlbumSortField.entries,
        label = { it.label },
        onSortChosen = viewModel::onSortChosen,
        onBack = onBack,
        isEmpty = state.isLoaded && state.albums.isEmpty(),
        miniPlayer = miniPlayer,
        modifier = modifier,
    ) {
        items(state.albums, key = { it.id }) { album ->
            AlbumRow(title = album.title, subtitle = albumSubtitle(album), onClick = { onOpenAlbum(album.id) })
        }
    }
}

private val AlbumSortField.label: Int
    get() =
        when (this) {
            AlbumSortField.Title -> R.string.albums_sort_title_field
            AlbumSortField.Artist -> R.string.albums_sort_artist
            AlbumSortField.Year -> R.string.albums_sort_year
            AlbumSortField.TrackCount -> R.string.albums_sort_track_count
            AlbumSortField.DateAdded -> R.string.albums_sort_date_added
        }

/** Album artists under the title; the mockup shows only them, the number of tracks is on the card. */
@Composable
internal fun albumSubtitle(album: Album): String = album.artistsText.ifEmpty { stringResource(R.string.unknown_artist) }

/** "13 tracks". */
@Composable
internal fun trackCountText(count: Int): String = pluralStringResource(R.plurals.library_track_count, count, count)
