package org.muzzzlo.player.feature.playlists

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import org.muzzzlo.player.core.designsystem.component.CoverImage
import org.muzzzlo.player.core.designsystem.component.CoverSource
import org.muzzzlo.player.core.designsystem.component.RoundIconButton
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.model.AutoPlaylist
import org.muzzzlo.player.core.designsystem.R as DesignR

// The same frame pieces as in `feature:library`; features do not depend on each other, so they are repeated here.

/** Space under a list so its last row can scroll out from under the mini player (DESIGN.md, section 6). */
internal val MiniPlayerClearance =
    PaddingValues(bottom = MuzzzloDimens.MiniPlayerHeight + MuzzzloDimens.SectionGap)

/** Top row of a screen: the back button (3:6) on the left and the screen's own buttons on the right. */
@Composable
internal fun ScreenHeader(
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(
            icon = DesignR.drawable.ic_chevron_left,
            contentDescription = stringResource(R.string.playlists_back),
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
        contentDescription = stringResource(R.string.playlists_home),
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

/** Centered secondary text for empty lists. */
@Composable
internal fun Notice(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MuzzzloColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** "13 tracks". */
@Composable
internal fun trackCountText(count: Int): String = pluralStringResource(R.plurals.playlist_track_count, count, count)

/** A playlist stands for itself with the cover of its first track; an empty playlist shows the placeholder. */
@Composable
internal fun PlaylistCover(
    firstTrackId: Long?,
    modifier: Modifier,
) = CoverImage(firstTrackId?.let { CoverSource.Track(it) }, modifier)

/** The name of an automatic playlist. */
@Composable
internal fun AutoPlaylist.title(): String =
    stringResource(
        when (this) {
            AutoPlaylist.Favorites -> R.string.auto_playlist_favorites
            AutoPlaylist.TopSongs -> R.string.auto_playlist_top_songs
        },
    )

/** What an empty automatic playlist says about how to fill it. */
@Composable
internal fun AutoPlaylist.emptyText(): String =
    stringResource(
        when (this) {
            AutoPlaylist.Favorites -> R.string.auto_playlist_favorites_empty
            AutoPlaylist.TopSongs -> R.string.auto_playlist_top_songs_empty
        },
    )
