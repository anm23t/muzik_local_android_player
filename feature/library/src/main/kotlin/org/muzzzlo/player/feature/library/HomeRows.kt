package org.muzzzlo.player.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.muzzzlo.player.core.designsystem.component.seasonName
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.model.Album
import org.muzzzlo.player.core.model.CalendarPeriod
import org.muzzzlo.player.core.model.ContinueItem
import org.muzzzlo.player.core.model.CoverGradients
import org.muzzzlo.player.core.model.GeneratedKind
import org.muzzzlo.player.core.model.GeneratedPlaylist
import java.time.LocalDate
import org.muzzzlo.player.core.designsystem.R as DesignR

/** The short line under the tile of a seasonal set. */
private val SEASON_NOTE = DesignR.string.generated_tile_season
private val YEAR_NOTE = DesignR.string.generated_tile_year

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
        val (title, subtitle) = generatedTileName(playlist)
        CoverTile(
            title = title,
            subtitle = subtitle,
            onClick = { onOpen(playlist.kind) },
            modifier = Modifier.width(width),
            cover = { GradientCover(playlist.gradient, it, MuzzzloDimens.TileShape) },
        )
    }
}

/**
 * The summaries of the season and the year (task 2.5.13), in the row under "Playlists for you": a gradient tile of
 * the season or the year named like it (not in the mockup, the tiles of 28:620, DESIGN.md, section 7).
 *
 * @param onOpen opens the slides of the period with this `CalendarPeriod.key`.
 */
@Composable
internal fun YourSummaries(
    periods: List<CalendarPeriod>,
    onOpen: (String) -> Unit,
) {
    if (periods.isEmpty()) return
    TileRow(stringResource(R.string.home_your_summaries), periods, key = { it.key }) { period, width ->
        CoverTile(
            title = summaryName(period),
            onClick = { onOpen(period.key) },
            modifier = Modifier.width(width),
            cover = { GradientCover(CoverGradients.forPeriod(period), it, MuzzzloDimens.TileShape) },
        )
    }
}

/**
 * The name of a generated playlist on a tile: a seasonal one is too long for one line ("Most played: Autumn 2026"),
 * so the tile shows the period with a short "Season top" or "Year top" under it; the card shows the whole name.
 */
@Composable
private fun generatedTileName(playlist: GeneratedPlaylist): Pair<String, String?> {
    val period = playlist.period
    return if (playlist.kind.isSeasonal && period != null) {
        val note = if (period is CalendarPeriod.Year) YEAR_NOTE else SEASON_NOTE
        summaryName(period) to stringResource(note)
    } else {
        generatedName(playlist) to null
    }
}

/** A season by its name, a year by its number; the home row shows nothing else. */
@Composable
private fun summaryName(period: CalendarPeriod): String =
    when (period) {
        is CalendarPeriod.SeasonOf -> seasonName(period.season.ordinal, period.year)
        else -> period.start.year.toString()
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
            val listState = rememberLazyListState()
            // A row keeps its first visible tile by key; a new tile in front ("Continue listening" after a listen)
            // would stay hidden behind the left edge, so a new first tile brings the row back to the start.
            val firstKey = items.firstOrNull()?.let(key)
            LaunchedEffect(firstKey) { listState.scrollToItem(0) }
            LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(gap)) {
                items(items, key = key) { tile(it, tileWidth) }
            }
        }
    }
}

/** The name of a generated playlist. */
@Composable
internal fun generatedName(playlist: GeneratedPlaylist): String =
    when (playlist.kind) {
        GeneratedKind.DAILY_MIX -> stringResource(DesignR.string.generated_daily_mix)
        GeneratedKind.ON_REPEAT -> stringResource(DesignR.string.generated_on_repeat)
        GeneratedKind.FORGOTTEN_FAVORITES -> stringResource(DesignR.string.generated_forgotten_favorites)
        GeneratedKind.BEST_RATED -> stringResource(DesignR.string.generated_best_rated)
        GeneratedKind.RECENTLY_ADDED -> stringResource(DesignR.string.generated_recently_added)
        GeneratedKind.MOST_PLAYED_SEASON, GeneratedKind.MOST_PLAYED_YEAR ->
            when (val period = playlist.period) {
                is CalendarPeriod.SeasonOf ->
                    stringResource(
                        DesignR.string.generated_most_played_season,
                        seasonName(period.season.ordinal, period.year),
                    )
                is CalendarPeriod.Year -> stringResource(DesignR.string.generated_most_played_year, period.year)
                else ->
                    stringResource(
                        DesignR.string.generated_most_played_year,
                        LocalDate.ofEpochDay(playlist.epochDay).year,
                    )
            }
    }

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
                    title = generatedTileName(item.playlist).first,
                    subtitle = generatedTileName(item.playlist).second,
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
