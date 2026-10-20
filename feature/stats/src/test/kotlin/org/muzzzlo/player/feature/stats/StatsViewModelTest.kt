package org.muzzzlo.player.feature.stats

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.model.StatsPeriod
import org.muzzzlo.player.core.model.StatsRepository
import org.muzzzlo.player.core.model.StatsTotals
import org.muzzzlo.player.core.model.TopAlbum
import org.muzzzlo.player.core.model.TopArtist
import org.muzzzlo.player.core.model.TopTrack

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {
    private val song = TopTrack(1, "Song", listOf("A", "B"), plays = 3, playedMs = 600_000)
    private val record = TopAlbum(2, "Record", plays = 3, playedMs = 600_000)
    private val artist = TopArtist(3, "A", plays = 3, playedMs = 600_000)

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(repository: FakeStats): StatsViewModel {
        val viewModel = StatsViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        return viewModel
    }

    @Test
    fun `the week is chosen first and its numbers are shown`() =
        runTest {
            val repository = FakeStats()
            repository.set(StatsPeriod.Week, StatsTotals(3, 1, 600_000), listOf(song), listOf(record), listOf(artist))
            val state = viewModel(repository).state.value

            assertEquals(StatsPeriod.Week, state.period)
            assertEquals(StatsTotals(3, 1, 600_000), state.totals)
            assertEquals(listOf(song), state.tracks)
            assertEquals(listOf(record), state.albums)
            assertEquals(listOf(artist), state.artists)
            assertTrue(state.isLoaded)
            assertFalse(state.isEmpty)
        }

    @Test
    fun `choosing a period shows the numbers of that period`() =
        runTest {
            val repository = FakeStats()
            repository.set(StatsPeriod.Week, StatsTotals(3, 0, 600_000), listOf(song), emptyList(), emptyList())
            repository.set(StatsPeriod.Today, StatsTotals(1, 0, 200_000), emptyList(), emptyList(), emptyList())
            val viewModel = viewModel(repository)

            viewModel.onPeriodChosen(StatsPeriod.Today)

            assertEquals(StatsPeriod.Today, viewModel.state.value.period)
            assertEquals(1, viewModel.state.value.totals.plays)
            assertEquals(emptyList<TopTrack>(), viewModel.state.value.tracks)
        }

    @Test
    fun `a period without listens is empty`() =
        runTest {
            val viewModel = viewModel(FakeStats())
            viewModel.onPeriodChosen(StatsPeriod.AllTime)

            assertTrue(viewModel.state.value.isEmpty)
        }

    @Test
    fun `new listens reach the screen without choosing the period again`() =
        runTest {
            val repository = FakeStats()
            val viewModel = viewModel(repository)
            assertTrue(viewModel.state.value.isEmpty)

            repository.set(StatsPeriod.Week, StatsTotals(1, 0, 100_000), listOf(song), emptyList(), emptyList())

            assertEquals(1, viewModel.state.value.totals.plays)
            assertFalse(viewModel.state.value.isEmpty)
        }

    @Test
    fun `the offered periods are the five calendar ones`() {
        assertEquals(
            listOf(StatsPeriod.Today, StatsPeriod.Week, StatsPeriod.Month, StatsPeriod.Year, StatsPeriod.AllTime),
            statsPeriods,
        )
    }

    private class FakeStats : StatsRepository {
        private data class Answer(
            val totals: StatsTotals,
            val tracks: List<TopTrack>,
            val albums: List<TopAlbum>,
            val artists: List<TopArtist>,
        )

        private val answers = MutableStateFlow<Map<StatsPeriod, Answer>>(emptyMap())
        private val none = Answer(StatsTotals(0, 0, 0), emptyList(), emptyList(), emptyList())

        fun set(
            period: StatsPeriod,
            totals: StatsTotals,
            tracks: List<TopTrack>,
            albums: List<TopAlbum>,
            artists: List<TopArtist>,
        ) {
            answers.value = answers.value + (period to Answer(totals, tracks, albums, artists))
        }

        private fun <T> answer(
            period: StatsPeriod,
            pick: (Answer) -> T,
        ): Flow<T> = answers.map { pick(it[period] ?: none) }

        override fun totals(period: StatsPeriod) = answer(period) { it.totals }

        override fun topTracks(
            period: StatsPeriod,
            limit: Int,
        ) = answer(period) { it.tracks }

        override fun topAlbums(
            period: StatsPeriod,
            limit: Int,
        ) = answer(period) { it.albums }

        override fun topArtists(
            period: StatsPeriod,
            limit: Int,
        ) = answer(period) { it.artists }

        override fun recentAlbumIds(limit: Int) = flowOf(emptyList<Long>())
    }
}
