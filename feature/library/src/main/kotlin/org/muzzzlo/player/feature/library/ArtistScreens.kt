package org.muzzzlo.player.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.ActionRow
import org.muzzzlo.player.core.designsystem.component.ArtistRow
import org.muzzzlo.player.core.designsystem.component.CoverTile
import org.muzzzlo.player.core.designsystem.component.MuzzzloDialog
import org.muzzzlo.player.core.designsystem.component.OutlinedPanel
import org.muzzzlo.player.core.designsystem.component.PillButton
import org.muzzzlo.player.core.designsystem.component.ScreenFrame
import org.muzzzlo.player.core.designsystem.component.SectionTitle
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.model.Album
import org.muzzzlo.player.core.model.Artist
import org.muzzzlo.player.core.designsystem.R as DesignR

/**
 * Artist list (23:403): the same screen as the album list with artist rows and their photos.
 *
 * @param miniPlayer the mini player of `feature:player`, passed in by the app.
 */
@Composable
fun ArtistListScreen(
    onBack: () -> Unit,
    onOpenArtist: (Long) -> Unit,
    miniPlayer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArtistListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SortableListScreen(
        title = R.string.artists_title,
        sort = state.sort,
        fields = ArtistSortField.entries,
        label = { it.label },
        onSortChosen = viewModel::onSortChosen,
        onBack = onBack,
        isEmpty = state.isLoaded && state.artists.isEmpty(),
        miniPlayer = miniPlayer,
        modifier = modifier,
    ) {
        items(state.artists, key = { it.id }) { artist ->
            ArtistRow(
                name = artist.name,
                onClick = { onOpenArtist(artist.id) },
                photo =
                    artist.photoPath?.let { path ->
                        @Composable {
                                photoModifier: Modifier ->
                            ArtistPhoto(path, MuzzzloDimens.ArtistPhoto, photoModifier)
                        }
                    },
            )
        }
    }
}

private val ArtistSortField.label: Int
    get() =
        when (this) {
            ArtistSortField.Name -> R.string.artists_sort_name
            ArtistSortField.TrackCount -> R.string.artists_sort_track_count
            ArtistSortField.DateAdded -> R.string.artists_sort_date_added
        }

/**
 * Artist card (33:714) with back and home on top (owner's decision, DESIGN.md, section 3). A long press on the
 * photo picks a new one. Play counts and the top songs wait for statistics (0.6) and show an empty state.
 *
 * @param onOpenSongs opens the list of all songs of the artist ("All Songs").
 * @param miniPlayer the mini player of `feature:player`, passed in by the app.
 */
@Composable
fun ArtistScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenSongs: (Long) -> Unit,
    miniPlayer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var photoMenuOpen by rememberSaveable { mutableStateOf(false) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { viewModel.onPhotoPicked(it.toString()) }
        }
    val pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    ScreenFrame(modifier, outlined = false) {
        ScreenHeader(onBack = onBack) { HomeButton(onHome) }
        val artist = state.artist
        if (artist == null) {
            if (state.isLoaded) Notice(stringResource(R.string.artist_not_found), Modifier.weight(1f))
            return@ScreenFrame
        }
        ContentWithMiniPlayer(miniPlayer) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(MiniPlayerClearance),
                verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.SectionGap),
            ) {
                ArtistHeader(
                    artist,
                    onPhotoLongPress = { if (artist.photoPath == null) pickPhoto() else photoMenuOpen = true },
                )
                TopSongs()
                ArtistAlbums(state.albums, onOpenAlbum, onOpenSongs = { onOpenSongs(artist.id) })
            }
        }
        if (photoMenuOpen) {
            PhotoMenu(
                artistName = artist.name,
                onPick = pickPhoto,
                onRemove = viewModel::onPhotoRemoved,
                onDismiss = { photoMenuOpen = false },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistHeader(
    artist: Artist,
    onPhotoLongPress: () -> Unit,
) {
    OutlinedPanel {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.RowGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtistPhoto(
                path = artist.photoPath,
                size = MuzzzloDimens.ArtistCardPhoto,
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = onPhotoLongPress,
                            onLongClickLabel = stringResource(R.string.artist_photo_change),
                        ),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.displaySmall,
                    color = MuzzzloColors.TextPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(R.plurals.artist_song_count, artist.trackCount, artist.trackCount),
                    style = MaterialTheme.typography.titleSmall,
                    color = MuzzzloColors.TextSecondary,
                )
            }
        }
    }
}

/** "Top Song / Streams" (33:714). Empty until listening statistics arrive in 0.6. */
@Composable
private fun TopSongs() {
    OutlinedPanel {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.ContentGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.artist_top_songs),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = MuzzzloColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.artist_streams),
                style = MaterialTheme.typography.titleLarge,
                color = MuzzzloColors.TextPrimary,
            )
        }
        Text(
            text = stringResource(R.string.artist_top_songs_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MuzzzloColors.TextSecondary,
        )
    }
}

@Composable
private fun ArtistAlbums(
    albums: List<Album>,
    onOpenAlbum: (Long) -> Unit,
    onOpenSongs: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.ContentGap)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(stringResource(R.string.artist_albums), Modifier.weight(1f))
            PillButton(text = stringResource(R.string.artist_all_songs), onClick = onOpenSongs)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.SectionGap)) {
            items(albums, key = { it.id }) { album ->
                CoverTile(
                    title = album.title,
                    subtitle = albumSubtitle(album),
                    onClick = { onOpenAlbum(album.id) },
                    modifier = Modifier.width(MuzzzloDimens.ArtistCardTile),
                )
            }
        }
    }
}

/** Shown on a long press when the artist already has a photo. Not in the mockup (DESIGN.md, section 7). */
@Composable
private fun PhotoMenu(
    artistName: String,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    MuzzzloDialog(title = artistName, onDismiss = onDismiss) {
        ActionRow(
            icon = DesignR.drawable.ic_pencil,
            text = stringResource(R.string.artist_photo_choose),
            onClick = {
                onDismiss()
                onPick()
            },
        )
        ActionRow(
            icon = DesignR.drawable.ic_close,
            text = stringResource(R.string.artist_photo_remove),
            onClick = {
                onDismiss()
                onRemove()
            },
        )
    }
}
