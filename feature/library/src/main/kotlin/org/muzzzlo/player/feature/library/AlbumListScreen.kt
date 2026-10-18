package org.muzzzlo.player.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.AlbumRow
import org.muzzzlo.player.core.designsystem.component.MuzzzloDialog
import org.muzzzlo.player.core.designsystem.component.PillButton
import org.muzzzlo.player.core.designsystem.component.RadioOptionRow
import org.muzzzlo.player.core.designsystem.component.ScreenFrame
import org.muzzzlo.player.core.designsystem.component.SectionTitle
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.model.Album
import org.muzzzlo.player.core.designsystem.R as DesignR

/**
 * Album list (15:3): back, the sort button, the title and the albums. The list scrolls under the mini player.
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
    var sortOpen by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // A new order starts from the top. The list keeps the first visible album in place by its key,
    // which after a resort shows the middle of the list. Coming back to the screen keeps the position.
    var shownSort by rememberSaveable(stateSaver = AlbumSortSaver) { mutableStateOf(state.sort) }
    LaunchedEffect(state.sort) {
        if (state.sort != shownSort) {
            shownSort = state.sort
            listState.scrollToItem(0)
        }
    }

    ScreenFrame(modifier) {
        ScreenHeader(onBack = onBack) {
            PillButton(
                text = stringResource(R.string.albums_sort),
                onClick = { sortOpen = true },
                icon = sortIcon(state.sort.ascending),
            )
        }
        SectionTitle(stringResource(R.string.albums_title))
        ContentWithMiniPlayer(miniPlayer) {
            if (state.isLoaded && state.albums.isEmpty()) {
                Notice(stringResource(R.string.library_empty))
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = MiniPlayerClearance,
                    verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.TinyGap),
                ) {
                    items(state.albums, key = { it.id }) { album ->
                        AlbumRow(
                            title = album.title,
                            subtitle = albumSubtitle(album),
                            onClick = { onOpenAlbum(album.id) },
                        )
                    }
                }
            }
        }
    }
    if (sortOpen) {
        SortDialog(state.sort, viewModel::onSortChosen, onDismiss = { sortOpen = false })
    }
}

/**
 * Sort dialog (15:111). A tap applies the option at once and a second tap flips the direction, "Done" closes.
 * "New sort" (15:114) is a reserve for later and is not shown (DESIGN.md, section 3).
 */
@Composable
private fun SortDialog(
    sort: AlbumSort,
    onChoose: (AlbumSortField) -> Unit,
    onDismiss: () -> Unit,
) {
    MuzzzloDialog(title = stringResource(R.string.albums_sort_title), onDismiss = onDismiss) {
        AlbumSortField.entries.forEach { field ->
            RadioOptionRow(
                text = stringResource(field.label),
                selected = field == sort.field,
                onClick = { onChoose(field) },
            )
        }
        PillButton(text = stringResource(R.string.albums_sort_done), onClick = onDismiss, wide = true)
    }
}

/** Keeps [AlbumSort] across configuration changes as the field name and the direction. */
private val AlbumSortSaver =
    listSaver<AlbumSort, Any>(
        save = { listOf(it.field.name, it.ascending) },
        restore = { AlbumSort(AlbumSortField.valueOf(it[0] as String), it[1] as Boolean) },
    )

/**
 * Icon of the sort button. The mockup draws the default order (A to Z) with "arrow-down-wide-narrow",
 * the opposite direction uses the mirrored "arrow-up-wide-narrow".
 */
private fun sortIcon(ascending: Boolean): Int =
    if (ascending) DesignR.drawable.ic_arrow_down_wide_narrow else DesignR.drawable.ic_arrow_up_wide_narrow

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
