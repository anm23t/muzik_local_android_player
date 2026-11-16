package org.muzzzlo.player.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import org.muzzzlo.player.core.designsystem.component.CoverImage
import org.muzzzlo.player.core.designsystem.component.CoverSource
import org.muzzzlo.player.core.designsystem.component.CoverTile
import org.muzzzlo.player.core.designsystem.component.GradientCover
import org.muzzzlo.player.core.designsystem.component.SectionTitle
import org.muzzzlo.player.core.designsystem.component.playlistCoverSource
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.model.Album
import org.muzzzlo.player.core.model.ContinueItem
import org.muzzzlo.player.core.model.GeneratedKind
import org.muzzzlo.player.core.model.GeneratedPlaylist
import org.muzzzlo.player.core.designsystem.R as DesignR

/** Tiles in one row of the home screen (28:620). */
private const val TILES_PER_ROW = 3

/** "Playlists for you" (28:620): today's generated playlists, gradient tiles with the name under them. */
@Composable
internal fun PlaylistsForYou(
    playlists: List<GeneratedPlaylist>,
    onOpen: (GeneratedKind) -> Unit,
) {
    if (playlists.isEmpty()) return
    TileRow(stringResource(R.string.home_playlists_for_you), playlists, key = { it.kind.key }) { playlist, width ->
        CoverTile(
            title = generatedName(playlist.kind),
            onClick = { onOpen(playlist.kind) },
            modifier = Modifier.width(width),
            cover = { GradientCover(playlist.gradient, it, MuzzzloDimens.TileShape) },
        )
    }
}

/** A titled row of tiles; three fill the width (28:620), the others wait behind the edge. */
@Composable
private fun <T> TileRow(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    tile: @Composable (T, Dp) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.ContentGap)) {
        SectionTitle(title, maxLines = 2)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = MuzzzloDimens.SectionGap
            val tileWidth = (maxWidth - gap * (TILES_PER_ROW - 1)) / TILES_PER_ROW
            LazyRow(horizontalArrangement = Arrangement.spacedBy(gap)) {
                items(items, key = key) { tile(it, tileWidth) }
            }
        }
    }
}

/** The name of a generated playlist. */
@Composable
internal fun generatedName(kind: GeneratedKind): String =
    stringResource(
        when (kind) {
            GeneratedKind.ON_REPEAT -> DesignR.string.generated_on_repeat
            GeneratedKind.FORGOTTEN_FAVORITES -> DesignR.string.generated_forgotten_favorites
            GeneratedKind.BEST_RATED -> DesignR.string.generated_best_rated
            GeneratedKind.RECENTLY_ADDED -> DesignR.string.generated_recently_added
        },
    )

@Composable
internal fun ContinueListening(
    items: List<ContinueItem>,
    onOpenAlbum: (Long) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenGenerated: (GeneratedKind) -> Unit,
) {
    if (items.isEmpty()) return
    TileRow(stringResource(R.string.home_continue_listening), items, key = ::tileKey) { item, tileWidth ->
        when (item) {
            is ContinueItem.AlbumItem ->
                CoverTile(
                    title = item.album.title,
                    onClick = { onOpenAlbum(item.album.id) },
                    modifier = Modifier.width(tileWidth),
                    cover = { CoverImage(CoverSource.Album(item.album.id), it, MuzzzloDimens.TileShape) },
                )
            // A playlist shows its own picture or the cover of its first track, like in the list of
            // playlists.
            is ContinueItem.PlaylistItem ->
                CoverTile(
                    title = item.playlist.name,
                    onClick = { onOpenPlaylist(item.playlist.id) },
                    modifier = Modifier.width(tileWidth),
                    cover = {
                        val playlist = item.playlist
                        val source =
                            playlistCoverSource(
                                playlist.coverPath,
                                playlist.firstTrackId,
                                playlist.coverGradient,
                            )
                        CoverImage(source, it, MuzzzloDimens.TileShape)
                    },
                )
            is ContinueItem.GeneratedItem ->
                CoverTile(
                    title = generatedName(item.playlist.kind),
                    onClick = { onOpenGenerated(item.playlist.kind) },
                    modifier = Modifier.width(tileWidth),
                    cover = { GradientCover(item.playlist.gradient, it, MuzzzloDimens.TileShape) },
                )
        }
    }
}

private fun tileKey(item: ContinueItem): String =
    when (item) {
        is ContinueItem.AlbumItem -> "album-${item.album.id}"
        is ContinueItem.PlaylistItem -> "playlist-${item.playlist.id}"
        is ContinueItem.GeneratedItem -> "generated-${item.playlist.kind.key}"
    }
