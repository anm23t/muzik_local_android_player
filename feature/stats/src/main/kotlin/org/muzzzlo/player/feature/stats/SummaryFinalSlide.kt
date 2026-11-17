package org.muzzzlo.player.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.muzzzlo.player.core.designsystem.component.CoverImage
import org.muzzzlo.player.core.designsystem.component.CoverSource
import org.muzzzlo.player.core.designsystem.component.SectionTitle
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.designsystem.theme.MuzzzloTheme
import org.muzzzlo.player.core.model.PeriodSummary

/** The final slide of a summary (task 2.5.12): the top track, album and artist, the minutes, the favorite genre. */
@Composable
internal fun FinalSlide(summary: PeriodSummary) {
    SectionTitle(stringResource(R.string.summary_final_title))
    Text(
        text = periodTitle(summary.period),
        style = MaterialTheme.typography.titleMedium,
        color = MuzzzloColors.TextSecondary,
    )
    summary.topTrack?.let { track ->
        FinalRow(stringResource(R.string.summary_top_track), track.title) {
            CoverImage(track.trackId?.let { CoverSource.Track(it) }, it)
        }
    }
    summary.topAlbum?.let { album ->
        FinalRow(
            stringResource(R.string.summary_top_album),
            album.title,
        ) { CoverImage(CoverSource.Album(album.albumId), it) }
    }
    summary.topArtist?.let { artist ->
        FinalRow(stringResource(R.string.summary_top_artist), artist.name) { ArtistPicture(artist, it) }
    }
    FinalFigure(stringResource(R.string.summary_minutes_label), minutesOf(summary.playedMs).toString())
    summary.favoriteGenre?.let { FinalFigure(stringResource(R.string.summary_genre_label), it) }
}

@Composable
private fun FinalRow(
    label: String,
    name: String,
    picture: @Composable (Modifier) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.ContentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        picture(Modifier.size(MuzzzloDimens.ListCover))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MuzzzloColors.TextSecondary)
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = MuzzzloColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FinalFigure(
    label: String,
    value: String,
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MuzzzloColors.TextSecondary)
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MuzzzloTheme.accent.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
