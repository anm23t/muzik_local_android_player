package org.muzzzlo.player.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import org.muzzzlo.player.core.model.StatsPeriod
import org.muzzzlo.player.core.model.StatsRepository
import org.muzzzlo.player.core.model.StatsTotals
import org.muzzzlo.player.core.model.TopAlbum
import org.muzzzlo.player.core.model.TopArtist
import org.muzzzlo.player.core.model.TopTrack
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

/** The periods the screen offers, in the order of the period row. [StatsPeriod.Custom] is not offered yet. */
val statsPeriods: List<StatsPeriod> =
    listOf(StatsPeriod.Today, StatsPeriod.Week, StatsPeriod.Month, StatsPeriod.Year, StatsPeriod.AllTime)

/**
 * State of the statistics screen: the chosen [period] and what was listened to in it.
 *
 * @property isLoaded false until the first answer of the repository for the chosen period.
 */
data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.Week,
    val totals: StatsTotals = StatsTotals(plays = 0, skips = 0, playedMs = 0),
    val tracks: List<TopTrack> = emptyList(),
    val albums: List<TopAlbum> = emptyList(),
    val artists: List<TopArtist> = emptyList(),
    val isLoaded: Boolean = false,
) {
    /** Nothing was listened to in the period, so the screen shows a notice instead of empty tops. */
    val isEmpty: Boolean get() = isLoaded && totals.plays == 0 && totals.skips == 0
}

/** Basic statistics screen (0.6.3): totals and the three tops of one period. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel
    @Inject
    constructor(
        private val repository: StatsRepository,
    ) : ViewModel() {
        private val period = MutableStateFlow<StatsPeriod>(StatsPeriod.Week)

        val state: StateFlow<StatsUiState> =
            period
                .flatMapLatest { chosen ->
                    combine(
                        repository.totals(chosen),
                        repository.topTracks(chosen),
                        repository.topAlbums(chosen),
                        repository.topArtists(chosen),
                    ) { totals, tracks, albums, artists ->
                        StatsUiState(chosen, totals, tracks, albums, artists, isLoaded = true)
                    }.onStart { emit(StatsUiState(period = chosen)) }
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StatsUiState())

        fun onPeriodChosen(chosen: StatsPeriod) {
            period.value = chosen
        }
    }
