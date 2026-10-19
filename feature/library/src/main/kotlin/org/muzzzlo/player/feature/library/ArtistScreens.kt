package org.muzzzlo.player.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import org.muzzzlo.player.core.designsystem.component.CoverImage
import org.muzzzlo.player.core.designsystem.component.CoverSource
import org.muzzzlo.player.core.designsystem.component.CoverTile
import org.muzzzlo.player.core.designsystem.component.MuzzzloDialog
import org.muzzzlo.player.core.designsystem.component.OutlinedPanel
import org.muzzzlo.player.core.designsystem.component.PillButton
import org.muzzzlo.player.core.designsystem.component.RoundIconButton
import org.muzzzlo.player.core.designsystem.component.ScreenFrame
import org.muzzzlo.player.core.designsystem.component.SectionTitle
import org.muzzzlo.player.core.designsystem.component.TagTextField
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.model.Album
import org.muzzzlo.player.core.model.Artist
import org.muzzzlo.player.core.model.ArtistDiscography
import org.muzzzlo.player.core.model.CompoundNameProblem
import org.muzzzlo.player.core.designsystem.R as DesignR

/**
 * Artist list (23:403): the same screen as the album list with artist rows and their photos.
 *
 * @param onCheck opens the check of doubled artists.
 * @param miniPlayer the mini player of `feature:player`, passed in by the app.
 */
@Composable
fun ArtistListScreen(
    onBack: () -> Unit,
    onOpenArtist: (Long) -> Unit,
    onCheck: () -> Unit,
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
        headerAction = {
            if (!state.selecting) {
                PillButton(text = stringResource(R.string.artists_check), onClick = onCheck)
                Spacer(Modifier.width(MuzzzloDimens.ItemGap))
            }
            RoundIconButton(
                icon = if (state.selecting) DesignR.drawable.ic_close else DesignR.drawable.ic_merge,
                contentDescription =
                    stringResource(if (state.selecting) R.string.artists_select_cancel else R.string.artists_select),
                onClick = viewModel::onSelectionToggled,
            )
            Spacer(Modifier.width(MuzzzloDimens.ItemGap))
        },
        selectionBar =
            if (state.selecting) {
                @Composable {
                    PillButton(
                        text = stringResource(R.string.artists_remember),
                        onClick = viewModel::onRememberClicked,
                        wide = true,
                        enabled = state.selectedIds.size >= 2,
                    )
                }
            } else {
                null
            },
    ) {
        items(state.artists, key = { it.id }) { artist ->
            ArtistRow(
                name = artist.name,
                onClick = {
                    if (state.selecting) viewModel.onArtistTicked(artist.id) else onOpenArtist(artist.id)
                },
                selected = artist.id in state.selectedIds,
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
    state.remember?.let { dialog ->
        RememberDialog(
            dialog = dialog,
            onMove = viewModel::onPartMoved,
            onNameChange = viewModel::onNameChanged,
            onConfirm = viewModel::onRememberConfirmed,
            onDismiss = viewModel::onRememberDismissed,
        )
    }
}

/**
 * "Remember as one artist" (no mockup, DESIGN.md, section 7): the ticked artists in a list with up and down
 * buttons for the order, the name as it will be written, and "Save".
 */
@Composable
private fun RememberDialog(
    dialog: RememberState,
    onMove: (Int, Int) -> Unit,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    MuzzzloDialog(title = stringResource(R.string.remember_title), onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.remember_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MuzzzloColors.TextSecondary,
        )
        dialog.parts.forEachIndexed { index, part ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.ItemGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = part,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MuzzzloColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                RoundIconButton(
                    icon = DesignR.drawable.ic_arrow_up_wide_narrow,
                    contentDescription = stringResource(R.string.remember_move_up),
                    onClick = { onMove(index, -1) },
                )
                RoundIconButton(
                    icon = DesignR.drawable.ic_arrow_down_wide_narrow,
                    contentDescription = stringResource(R.string.remember_move_down),
                    onClick = { onMove(index, 1) },
                )
            }
        }
        TagTextField(
            label = stringResource(R.string.remember_name),
            value = dialog.name,
            onValueChange = onNameChange,
            error =
                when {
                    dialog.duplicate -> stringResource(R.string.remember_duplicate)
                    dialog.problem == CompoundNameProblem.TooShort -> stringResource(R.string.remember_too_short)
                    dialog.problem == CompoundNameProblem.NotSelectedParts ->
                        stringResource(
                            R.string.remember_not_parts,
                        )
                    else -> null
                },
        )
        PillButton(
            text = stringResource(R.string.remember_save),
            onClick = onConfirm,
            wide = true,
            enabled = !dialog.isSaving,
        )
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
 * @param onEditArtist opens the profile editor (rename, merge, split) of the artist with this id.
 * @param miniPlayer the mini player of `feature:player`, passed in by the app.
 */
@Composable
fun ArtistScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenSongs: (Long) -> Unit,
    onEditArtist: (Long) -> Unit,
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
        val artist = state.artist
        ScreenHeader(onBack = onBack) {
            if (artist != null) {
                RoundIconButton(
                    icon = DesignR.drawable.ic_pencil,
                    contentDescription = stringResource(R.string.artist_edit_profile),
                    onClick = { onEditArtist(artist.id) },
                )
                Spacer(Modifier.width(MuzzzloDimens.ItemGap))
            }
            HomeButton(onHome)
        }
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
                ArtistDiscographySections(
                    state.discography,
                    onOpenAlbum,
                    onOpenSongs = { onOpenSongs(artist.id) },
                )
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

/**
 * "Albums", "Singles" and "Appears on", each a title and a row of tiles; empty sections are hidden. The "All songs"
 * button sits in the header of the first visible section, or alone when the artist has no albums.
 */
@Composable
private fun ArtistDiscographySections(
    discography: ArtistDiscography,
    onOpenAlbum: (Long) -> Unit,
    onOpenSongs: () -> Unit,
) {
    val sections =
        listOf(
            R.string.artist_albums to discography.albums,
            R.string.artist_singles to discography.singles,
            R.string.artist_appears_on to discography.appearsOn,
        ).filter { (_, albums) -> albums.isNotEmpty() }
    if (sections.isEmpty()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PillButton(text = stringResource(R.string.artist_all_songs), onClick = onOpenSongs)
        }
        return
    }
    sections.forEachIndexed { index, (title, albums) ->
        AlbumSection(
            title = stringResource(title),
            albums = albums,
            onOpenAlbum = onOpenAlbum,
            onOpenSongs = onOpenSongs.takeIf { index == 0 },
        )
    }
}

/** One section of the artist card: [SectionTitle], the optional "All songs" button and a row of [CoverTile]. */
@Composable
private fun AlbumSection(
    title: String,
    albums: List<Album>,
    onOpenAlbum: (Long) -> Unit,
    onOpenSongs: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.ContentGap)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(title, Modifier.weight(1f))
            if (onOpenSongs != null) {
                PillButton(text = stringResource(R.string.artist_all_songs), onClick = onOpenSongs)
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.SectionGap)) {
            items(albums, key = { it.id }) { album ->
                CoverTile(
                    title = album.title,
                    subtitle = albumSubtitle(album),
                    onClick = { onOpenAlbum(album.id) },
                    modifier = Modifier.width(MuzzzloDimens.ArtistCardTile),
                    cover = { CoverImage(CoverSource.Album(album.id), it, MuzzzloDimens.TileShape) },
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
