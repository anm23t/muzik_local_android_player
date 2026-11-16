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
import org.muzzzlo.player.core.model.Album
import org.muzzzlo.player.core.model.Artist
import org.muzzzlo.player.core.model.ArtistDiscography
import org.muzzzlo.player.core.model.ArtistRef
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.PlayerState
import org.muzzzlo.player.core.model.RecentAlbum
import org.muzzzlo.player.core.model.StatsPeriod
import org.muzzzlo.player.core.model.StatsRepository
import org.muzzzlo.player.core.model.StatsTotals
import org.muzzzlo.player.core.model.TopAlbum
import org.muzzzlo.player.core.model.TopArtist
import org.muzzzlo.player.core.model.TopTrack
import org.muzzzlo.player.core.model.Track

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {
    private val song = TopTrack(1, "Song", listOf("A", "B"), plays = 3, playedMs = 600_000)
    private val record = TopAlbum(2, "Record", plays = 3, playedMs = 600_000)
    private val artist = TopArtist(3, "A", plays = 3, playedMs = 600_000)

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private val library = FakeLibrary()
    private val player = FakePlayer()

    private fun track(id: Long) = Track(id, "uri$id", "Song $id", listOf("A"), null, null, 1000, null)

    private fun top(
        id: Long?,
        plays: Int,
    ) = TopTrack(id, "Song $id", listOf("A"), plays, plays * 1000L)

    private fun TestScope.viewModel(repository: FakeStats): StatsViewModel {
        val viewModel = StatsViewModel(repository, library, player)
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

    @Test
    fun `tapping a top track plays the top from it in the order of the top`() =
        runTest {
            library.tracks = listOf(track(1), track(2), track(3))
            val repository = FakeStats()
            repository.set(
                StatsPeriod.Week,
                StatsTotals(6, 0, 6000),
                listOf(top(2, 3), top(3, 2), top(1, 1)),
                emptyList(),
                emptyList(),
            )
            val viewModel = viewModel(repository)

            viewModel.onTrackClicked(3)

            assertEquals(listOf(listOf(2L, 3L, 1L) to 1), player.playedIds())
        }

    @Test
    fun `deleted tracks are skipped in the queue and are not playable`() =
        runTest {
            library.tracks = listOf(track(1), track(3))
            val repository = FakeStats()
            repository.set(
                StatsPeriod.Week,
                StatsTotals(6, 0, 6000),
                listOf(top(1, 3), top(null, 2), top(2, 2), top(3, 1)),
                emptyList(),
                emptyList(),
            )
            val viewModel = viewModel(repository)

            assertEquals(listOf(1L, 3L), viewModel.state.value.playable.keys.toList())
            viewModel.onTrackClicked(3)

            assertEquals(listOf(listOf(1L, 3L) to 1), player.playedIds())
        }

    @Test
    fun `tapping a deleted track plays nothing`() =
        runTest {
            library.tracks = listOf(track(1))
            val repository = FakeStats()
            repository.set(
                StatsPeriod.Week,
                StatsTotals(2, 0, 2000),
                listOf(top(1, 1), top(2, 1)),
                emptyList(),
                emptyList(),
            )
            val viewModel = viewModel(repository)

            viewModel.onTrackClicked(2)

            assertTrue(player.played.isEmpty())
        }

    private class FakeLibrary : LibraryRepository {
        var tracks: List<Track> = emptyList()

        override suspend fun tracksByIds(ids: List<Long>) = ids.mapNotNull { id -> tracks.firstOrNull { it.id == id } }

        override fun observeTracks() = flowOf(tracks)

        override fun observeAlbums() = flowOf(emptyList<Album>())

        override fun observeAlbumTracks(albumId: Long) = flowOf(emptyList<Track>())

        override fun observeArtists() = flowOf(emptyList<Artist>())

        override fun observeArtistTracks(artistId: Long) = flowOf(emptyList<Track>())

        override fun observeArtistAlbums(artistId: Long) = flowOf(ArtistDiscography())

        override suspend fun artistId(name: String): Long? = null

        override suspend fun artistsOfTrack(trackId: Long) = emptyList<ArtistRef>()

        override suspend fun setArtistPhoto(
            artistId: Long,
            sourceUri: String,
        ) = Unit

        override suspend fun removeArtistPhoto(artistId: Long) = Unit

        override suspend fun scan() = Unit
    }

    private class FakePlayer : PlayerController {
        override val state = MutableStateFlow(PlayerState())
        val played = mutableListOf<Pair<List<Track>, Int>>()

        /** What was played: the ids of the queue and the start index of each call. */
        fun playedIds() = played.map { (queue, start) -> queue.map { it.id } to start }

        override fun connect() = Unit

        override fun play(
            tracks: List<Track>,
            startIndex: Int,
            playlistId: Long?,
            generatedKind: String?,
        ) {
            played += tracks to startIndex
        }

        override fun togglePlayPause() = Unit

        override fun next() = Unit

        override fun previous() = Unit

        override fun seekTo(positionMs: Long) = Unit

        override fun playNext(track: Track) = Unit

        override fun addToQueue(track: Track) = Unit

        override fun skipTo(index: Int) = Unit

        override fun moveQueueItem(
            from: Int,
            to: Int,
        ) = Unit

        override fun removeQueueItem(index: Int) = Unit

        override fun toggleShuffle() = Unit

        override fun cycleRepeatMode() = Unit
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

        override fun artistTopTracks(
            artistId: Long,
            limit: Int,
        ) = flowOf(emptyList<TopTrack>())

        override fun artistPlays(artistId: Long) = flowOf(0)

        override fun recentAlbums(limit: Int) = flowOf(emptyList<RecentAlbum>())
    }
}
