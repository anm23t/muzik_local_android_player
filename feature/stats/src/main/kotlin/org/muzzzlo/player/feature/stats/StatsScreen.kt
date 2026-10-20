package org.muzzzlo.player.feature.stats

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.ChoiceChip
import org.muzzzlo.player.core.designsystem.component.OutlinedPanel
import org.muzzzlo.player.core.designsystem.component.RoundIconButton
import org.muzzzlo.player.core.designsystem.component.ScreenFrame
import org.muzzzlo.player.core.designsystem.component.SectionTitle
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.model.StatsPeriod
import org.muzzzlo.player.core.model.StatsTotals
import org.muzzzlo.player.core.designsystem.R as DesignR

/**
 * Basic statistics screen (no mockup, DESIGN.md, section 7): the period row, the totals of the period in a framed
 * panel, then the top tracks, albums and artists.
 *
 * @param onOpenArtist opens the artist card by id, or by name when the artist exists only in deleted tracks.
 */
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (artistId: Long?, name: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScreenFrame(modifier) {
        Box(Modifier.fillMaxWidth()) {
            RoundIconButton(
                icon = DesignR.drawable.ic_chevron_left,
                contentDescription = stringResource(R.string.stats_back),
                onClick = onBack,
            )
        }
        SectionTitle(stringResource(R.string.stats_title))
        PeriodRow(state.period, viewModel::onPeriodChosen)
        when {
            !state.isLoaded -> Unit
            state.isEmpty -> Notice(stringResource(R.string.stats_empty))
            else -> StatsContent(state, onOpenAlbum, onOpenArtist)
        }
    }
}

@Composable
private fun PeriodRow(
    chosen: StatsPeriod,
    onChosen: (StatsPeriod) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.SmallGap),
    ) {
        statsPeriods.forEach { period ->
            ChoiceChip(
                text = stringResource(period.labelRes()),
                selected = period == chosen,
                onClick = { onChosen(period) },
            )
        }
    }
}

private fun StatsPeriod.labelRes(): Int =
    when (this) {
        StatsPeriod.Today -> R.string.stats_period_today
        StatsPeriod.Week -> R.string.stats_period_week
        StatsPeriod.Month -> R.string.stats_period_month
        StatsPeriod.Year -> R.string.stats_period_year
        StatsPeriod.AllTime -> R.string.stats_period_all_time
        is StatsPeriod.Custom -> R.string.stats_period_custom
    }

@Composable
private fun StatsContent(
    state: StatsUiState,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long?, String) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.ContentGap)) {
        item { TotalsPanel(state.totals) }
        if (state.tracks.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.stats_top_tracks)) }
            items(state.tracks.size) { index -> TopTrackRow(index + 1, state.tracks[index]) }
        }
        if (state.albums.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.stats_top_albums)) }
            items(state.albums.size) { index -> TopAlbumRow(state.albums[index], onOpenAlbum) }
        }
        if (state.artists.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.stats_top_artists)) }
            items(state.artists.size) { index -> TopArtistRow(state.artists[index], onOpenArtist) }
        }
    }
}

@Composable
private fun TotalsPanel(totals: StatsTotals) {
    OutlinedPanel {
        TotalsLine(stringResource(R.string.stats_plays), totals.plays.toString())
        TotalsLine(stringResource(R.string.stats_skips), totals.skips.toString())
        TotalsLine(stringResource(R.string.stats_time), playedText(totals.playedMs))
    }
}

@Composable
private fun TotalsLine(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MuzzzloColors.TextSecondary,
        )
        Text(text = value, style = MaterialTheme.typography.titleSmall, color = MuzzzloColors.TextPrimary)
    }
}

@Composable
private fun playedText(playedMs: Long): String {
    val time = playedTimeOf(playedMs)
    return if (time.hours > 0) {
        stringResource(R.string.stats_time_hours_minutes, time.hours, time.minutes)
    } else {
        stringResource(R.string.stats_time_minutes, time.minutes)
    }
}

@Composable
private fun Notice(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = MuzzzloColors.TextSecondary)
    }
}
