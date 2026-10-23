package org.muzzzlo.player.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.muzzzlo.player.core.designsystem.component.AddToPlaylist
import org.muzzzlo.player.core.designsystem.component.ArtistChoice
import org.muzzzlo.player.core.designsystem.component.RoundIconButton
import org.muzzzlo.player.core.designsystem.component.TrackAction
import org.muzzzlo.player.core.designsystem.component.TrackActionsMenu
import org.muzzzlo.player.core.designsystem.component.TrackMarksMenu
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.model.Track
import org.muzzzlo.player.core.designsystem.R as DesignR

/** Space under a list so its last row can scroll out from under the mini player (DESIGN.md, section 6). */
internal val MiniPlayerClearance =
    PaddingValues(bottom = MuzzzloDimens.MiniPlayerHeight + MuzzzloDimens.SectionGap)

/** Top row of a screen: the back button (3:6) on the left and the screen's own buttons on the right. */
@Composable
internal fun ScreenHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(
            icon = DesignR.drawable.ic_chevron_left,
            contentDescription = stringResource(R.string.library_back),
            onClick = onBack,
        )
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/** The home button of a header (4:311): opens the home screen. */
@Composable
internal fun HomeButton(onClick: () -> Unit) {
    RoundIconButton(
        icon = DesignR.drawable.ic_home,
        contentDescription = stringResource(R.string.library_home),
        onClick = onClick,
    )
}

/** Fills the rest of the frame with [content] and lays the mini player over its bottom edge. */
@Composable
internal fun ColumnScope.ContentWithMiniPlayer(
    miniPlayer: @Composable () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(Modifier.weight(1f).fillMaxWidth()) {
        content()
        Box(Modifier.align(Alignment.BottomCenter)) { miniPlayer() }
    }
}

/** Centered secondary text for empty lists and short notices. */
@Composable
internal fun Notice(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MuzzzloColors.TextSecondary)
    }
}

/**
 * Remembers which track has its menu open and shows [TrackActionsMenu] for it.
 * Returns the function that opens the menu.
 *
 * @param onOpenAlbum opens the card of the album with this id ("Go to album"); null leaves the item out.
 * @param onEditTags opens the tag editor of the track with this id.
 * @param loadArtists artists of the track with this id, for "Go to artist" and its picker.
 * @param onOpenArtist opens the card of the artist with this id ("Go to artist").
 * @param addToPlaylist "Add to playlist" for the track with this id.
 * @param trackMarks "Add to favorites" and "Rate" for the track with this id.
 */
@Composable
internal fun rememberTrackMenu(
    onAction: (Track, TrackAction) -> Unit,
    onOpenAlbum: ((Long) -> Unit)?,
    onEditTags: (Long) -> Unit,
    loadArtists: suspend (Long) -> List<ArtistChoice>,
    onOpenArtist: (Long) -> Unit,
    addToPlaylist: (Long) -> AddToPlaylist,
    trackMarks: (Long) -> TrackMarksMenu,
): (Track) -> Unit {
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    menuTrack?.let { track ->
        TrackActionsMenu(
            title = track.title,
            artists = track.artistsText,
            onAction = { action ->
                onAction(track, action)
                when (action) {
                    TrackAction.EditTags -> onEditTags(track.id)
                    else -> Unit
                }
            },
            onDismiss = { menuTrack = null },
            loadArtists = { loadArtists(track.id) },
            onOpenArtist = onOpenArtist,
            addToPlaylist = remember(track.id) { addToPlaylist(track.id) },
            marks = remember(track.id) { trackMarks(track.id) },
            shareUri = track.uri,
            albumId = track.albumId,
            onOpenAlbum = onOpenAlbum,
        )
    }
    return { menuTrack = it }
}
