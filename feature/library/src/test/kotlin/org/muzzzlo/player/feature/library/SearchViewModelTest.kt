package org.muzzzlo.player.feature.library

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.PlayerState
import org.muzzzlo.player.core.model.SearchIndex
import org.muzzzlo.player.core.model.SearchRepository
import org.muzzzlo.player.core.model.Track

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val duet = Track(1, "content://1", "Duet", listOf("Alpha", "Beta"), 10, "Album", 1_000, 1)
    private val solo = Track(2, "content://2", "Solo", listOf("Beta"), 10, "Album", 1_000, 2)
    private val album = Album(10, "Album", listOf("Alpha"), 2010, 2)
    private val artists = listOf(Artist(1, "Alpha", 1), Artist(2, "Beta", 2))

    private val index =
        MutableStateFlow(SearchIndex(listOf(duet, solo), listOf(album), artists, emptyList(), emptyList()))
    private val player = FakePlayerController()

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        SearchViewModel(
            search = FakeSearch(index),
            library = LibraryViewModelsTest.FakeLibrary(listOf(duet, solo), listOf(album), artists),
            player = player,
            playlists = FakePlaylists(),
            marks = FakeTrackMarks(),
            dispatchers = TestDispatchers(dispatcher),
            savedState = savedState,
        )

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a blank query shows the hint and no results`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            collect(viewModel)
            advanceTimeBy(SEARCH_DEBOUNCE_MS + 1)
            assertTrue(viewModel.state.value.isBlank)
            assertTrue(viewModel.state.value.results.isEmpty)
            assertFalse(viewModel.state.value.nothingFound)
        }

    @Test
    fun `results come after the pause and find the second artist of a track`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            collect(viewModel)
            viewModel.onQueryChanged("beta")
            assertEquals("beta", viewModel.state.value.query)
            assertTrue(viewModel.state.value.results.isEmpty, "nothing before the debounce")

            advanceTimeBy(SEARCH_DEBOUNCE_MS + 1)
            runCurrent()
            assertEquals(listOf(1L, 2L), viewModel.state.value.results.tracks.map { it.id })
            assertEquals(listOf(2L), viewModel.state.value.results.artists.map { it.id })
        }

    @Test
    fun `an unknown query says nothing was found`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            collect(viewModel)
            viewModel.onQueryChanged("zzz")
            advanceTimeBy(SEARCH_DEBOUNCE_MS + 1)
            runCurrent()
            assertTrue(viewModel.state.value.nothingFound)
        }

    @Test
    fun `the query survives in the saved state`() =
        runTest(dispatcher) {
            val savedState = SavedStateHandle()
            viewModel(savedState).onQueryChanged("solo")
            val again = viewModel(savedState)
            collect(again)
            advanceTimeBy(SEARCH_DEBOUNCE_MS + 1)
            runCurrent()
            assertEquals("solo", again.state.value.query)
            assertEquals(listOf(2L), again.state.value.results.tracks.map { it.id })
        }

    @Test
    fun `a tapped song plays the found songs from it, the current track is marked`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            collect(viewModel)
            viewModel.onQueryChanged("beta")
            advanceTimeBy(SEARCH_DEBOUNCE_MS + 1)
            runCurrent()
            player.state.value = PlayerState(currentTrack = solo)

            viewModel.onTrackClicked(1)
            assertEquals(listOf(listOf(duet, solo) to 1), player.played)
            assertEquals(2L, viewModel.state.value.currentTrackId)
        }

    @Test
    fun `a new library comes into the results`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            collect(viewModel)
            viewModel.onQueryChanged("new")
            advanceTimeBy(SEARCH_DEBOUNCE_MS + 1)
            runCurrent()
            assertTrue(viewModel.state.value.nothingFound)

            val fresh = Track(3, "content://3", "New song", listOf("Gamma"), null, null, 1_000, null)
            index.value = SearchIndex(listOf(fresh), emptyList(), emptyList(), emptyList(), emptyList())
            runCurrent()
            assertEquals(listOf(3L), viewModel.state.value.results.tracks.map { it.id })
        }

    private fun TestScope.collect(viewModel: SearchViewModel) {
        backgroundScope.launch { viewModel.state.collect {} }
    }

    private class FakeSearch(
        private val index: Flow<SearchIndex>,
    ) : SearchRepository {
        override fun observeIndex(): Flow<SearchIndex> = index
    }

    private class TestDispatchers(
        dispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val io = dispatcher
        override val main = dispatcher
        override val default = dispatcher
    }
}
