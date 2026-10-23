package org.muzzzlo.player.feature.stats

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import org.muzzzlo.player.core.designsystem.component.AlbumRow
import org.muzzzlo.player.core.designsystem.component.ArtistRow
import org.muzzzlo.player.core.designsystem.component.CoverImage
import org.muzzzlo.player.core.designsystem.component.CoverSource
import org.muzzzlo.player.core.designsystem.component.TrackRow
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.model.TopAlbum
import org.muzzzlo.player.core.model.TopArtist
import org.muzzzlo.player.core.model.TopTrack

@Composable
internal fun playsText(plays: Int): String = pluralStringResource(R.plurals.stats_plays_count, plays, plays)

// The tap does nothing: the row of a deleted track has no track to open, and playing from a top is not asked for.
@Composable
internal fun TopTrackRow(
    rank: Int,
    track: TopTrack,
) {
    TrackRow(
        title = track.title,
        onClick = {},
        number = rank.toString(),
        subtitle = track.artists.joinToString(", ").ifEmpty { null },
        // A deleted track has no cover left: the placeholder is shown.
        cover = { CoverImage(track.trackId?.let { id -> CoverSource.Track(id) }, it) },
        trailing = {
            Text(
                text = playsText(track.plays),
                style = MaterialTheme.typography.bodySmall,
                color = MuzzzloColors.TextSecondary,
                maxLines = 1,
            )
        },
    )
}

@Composable
internal fun TopAlbumRow(
    album: TopAlbum,
    onOpenAlbum: (Long) -> Unit,
) {
    AlbumRow(
        title = album.title,
        subtitle = playsText(album.plays),
        onClick = { onOpenAlbum(album.albumId) },
        cover = { CoverImage(CoverSource.Album(album.albumId), it) },
    )
}

@Composable
internal fun TopArtistRow(
    artist: TopArtist,
    onOpenArtist: (Long?, String) -> Unit,
) {
    ArtistRow(
        name = "${artist.name} · ${playsText(artist.plays)}",
        onClick = { onOpenArtist(artist.artistId, artist.name) },
    )
}
