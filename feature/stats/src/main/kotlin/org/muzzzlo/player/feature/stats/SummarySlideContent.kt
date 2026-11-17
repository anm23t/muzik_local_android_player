package org.muzzzlo.player.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import org.muzzzlo.player.core.designsystem.component.ArtistPhoto
import org.muzzzlo.player.core.designsystem.component.CoverImage
import org.muzzzlo.player.core.designsystem.component.CoverSource
import org.muzzzlo.player.core.designsystem.component.SectionTitle
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.designsystem.theme.MuzzzloTheme
import org.muzzzlo.player.core.model.ArtistImage
import org.muzzzlo.player.core.model.PeriodSummary
import org.muzzzlo.player.core.model.SummaryArtist

private const val MS_PER_MINUTE = 60_000L

/** Whole minutes of [ms], as the summary shows them. */
internal fun minutesOf(ms: Long): Int = (ms / MS_PER_MINUTE).toInt()

/**
 * One slide of the summary (task 2.5.12, no mockup, DESIGN.md, section 7). Everything here goes into the shared
 * picture, so the name of the app is at the bottom of every slide; the live background is drawn around it.
 *
 * @param timeSec the clock of the glints of the final slide.
 */
@Composable
internal fun SummarySlideContent(
    slide: SummarySlide,
    summary: PeriodSummary,
    timeSec: () -> Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        if (slide == SummarySlide.FINAL) SummaryGlints(timeSec, Modifier.matchParentSize())
        Column(
            Modifier.fillMaxSize().padding(MuzzzloDimens.FrameInnerPadding),
            verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.SectionGap),
        ) {
            when (slide) {
                SummarySlide.COVER -> CoverSlide(summary)
                SummarySlide.TRACKS -> TracksSlide(summary)
                SummarySlide.ALBUMS -> AlbumsSlide(summary)
                SummarySlide.ARTISTS -> ArtistsSlide(summary)
                SummarySlide.FINAL -> FinalSlide(summary)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.summary_app_name),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelLarge,
                color = MuzzzloColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ColumnScope.CoverSlide(summary: PeriodSummary) {
    Spacer(Modifier.weight(1f))
    Text(
        text = periodTitle(summary.period),
        style = MaterialTheme.typography.displaySmall,
        color = MuzzzloColors.TextPrimary,
    )
    if (!summary.isFinished) {
        Text(
            text = stringResource(R.string.summary_so_far),
            style = MaterialTheme.typography.titleMedium,
            color = MuzzzloTheme.accent.accent,
        )
    }
    Text(
        text = playsText(summary.plays),
        style = MaterialTheme.typography.bodyLarge,
        color = MuzzzloColors.TextPrimary,
    )
    Text(
        text = minutesText(summary.playedMs),
        style = MaterialTheme.typography.bodyLarge,
        color = MuzzzloColors.TextPrimary,
    )
}

@Composable
private fun minutesText(ms: Long): String =
    minutesOf(ms).let { pluralStringResource(R.plurals.summary_minutes, it, it) }

@Composable
private fun TracksSlide(summary: PeriodSummary) {
    SectionTitle(stringResource(R.string.stats_top_tracks))
    summary.tracks.forEachIndexed { index, track ->
        SlideRow(
            rank = index + 1,
            title = track.title,
            subtitle = track.artists.joinToString(", "),
            plays = track.plays,
        ) { CoverImage(track.trackId?.let { CoverSource.Track(it) }, it) }
    }
}

@Composable
private fun AlbumsSlide(summary: PeriodSummary) {
    SectionTitle(stringResource(R.string.stats_top_albums))
    summary.albums.forEachIndexed { index, album ->
        SlideRow(index + 1, album.title, subtitle = null, plays = album.plays) {
            CoverImage(CoverSource.Album(album.albumId), it)
        }
    }
}

@Composable
private fun ArtistsSlide(summary: PeriodSummary) {
    SectionTitle(stringResource(R.string.stats_top_artists))
    summary.artists.forEachIndexed { index, artist ->
        SlideRow(index + 1, artist.name, subtitle = null, plays = artist.plays) { ArtistPicture(artist, it) }
    }
}

/** The photo of the artist (round, as in the lists), else the cover of their album, else the placeholder. */
@Composable
internal fun ArtistPicture(
    artist: SummaryArtist,
    modifier: Modifier,
) {
    when (val image = artist.image) {
        is ArtistImage.Photo -> ArtistPhoto(image.path, MuzzzloDimens.ArtistPhoto, modifier)
        is ArtistImage.AlbumCover -> CoverImage(CoverSource.Album(image.albumId), modifier)
        null -> CoverImage(null, modifier)
    }
}

/** A place of a top: the rank, the picture, the name with a second line and the listens. */
@Composable
private fun SlideRow(
    rank: Int,
    title: String,
    subtitle: String?,
    plays: Int,
    picture: @Composable (Modifier) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.ItemGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = rank.toString(),
            modifier = Modifier.width(MuzzzloDimens.TrackNumberWidth),
            style = MaterialTheme.typography.titleMedium,
            color = MuzzzloTheme.accent.accent,
        )
        picture(Modifier.size(MuzzzloDimens.ArtistPhoto))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MuzzzloColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(subtitle?.ifEmpty { null }, playsText(plays)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MuzzzloColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
