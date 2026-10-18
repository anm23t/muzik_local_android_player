package org.muzzzlo.player.feature.library

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.muzzzlo.player.core.designsystem.component.TrackAction
import org.muzzzlo.player.core.model.Album
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.PlayerState
import org.muzzzlo.player.core.model.Track

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelsTest {
    private val first = Track(1, "content://1", "First", listOf("A"), 10, "Album", 1_000, 1)
    private val second = Track(2, "content://2", "Second", listOf("A", "B"), 10, "Album", 1_000, 2)
    private val album = Album(10, "Album", listOf("A"), 2010, 2, 100)
    private val older = Album(11, "Older", listOf("C"), 1999, 1, 50)

    private val library = FakeLibrary(listOf(first, second), listOf(album, older))
    private val player = FakePlayerController()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @ParameterizedTest
    @EnumSource(TrackAction::class)
    fun `only queue actions reach the player`(action: TrackAction) {
        TracksViewModel(library, player).onTrackAction(first, action)

        assertEquals(if (action == TrackAction.PlayNext) listOf(first) else emptyList<Track>(), player.playedNext)
        assertEquals(if (action == TrackAction.AddToQueue) listOf(first) else emptyList<Track>(), player.addedToQueue)
    }

    @Test
    fun `the album card plays the whole album from the tapped track`() =
        runTest {
            val viewModel = AlbumViewModel(library, player, SavedStateHandle(mapOf(ALBUM_ID_ARG to album.id)))
            subscribe(viewModel.state)

            viewModel.onTrackClicked(1)
            viewModel.onPlayAlbum()

            assertEquals(album, viewModel.state.value.album)
            assertEquals(listOf(listOf(first, second) to 1, listOf(first, second) to 0), player.played)
        }

    @Test
    fun `the album card marks the playing track`() =
        runTest {
            val viewModel = AlbumViewModel(library, player, SavedStateHandle(mapOf(ALBUM_ID_ARG to album.id)))
            subscribe(viewModel.state)

            player.state.value = PlayerState(currentTrack = second)

            assertEquals(second.id, viewModel.state.value.currentTrackId)
        }

    @Test
    fun `a tap outside the list does not start playback`() =
        runTest {
            val viewModel = TracksViewModel(library, player)
            subscribe(viewModel.state)

            viewModel.onTrackClicked(5)

            assertEquals(emptyList<Pair<List<Track>, Int>>(), player.played)
        }

    @Test
    fun `the album list follows the chosen sort and keeps it in the saved state`() =
        runTest {
            val savedState = SavedStateHandle()
            val viewModel = AlbumListViewModel(library, savedState)
            subscribe(viewModel.state)
            assertEquals(listOf(album, older), viewModel.state.value.albums)

            viewModel.onSortChosen(AlbumSortField.Year)
            assertEquals(listOf(older, album), viewModel.state.value.albums)

            viewModel.onSortChosen(AlbumSortField.Year)
            val restored = AlbumListViewModel(library, savedState)
            subscribe(restored.state)
            assertEquals(AlbumSort(AlbumSortField.Year, ascending = false), restored.state.value.sort)
            assertEquals(listOf(album, older), restored.state.value.albums)
        }

    @Test
    fun `an unknown saved sort falls back to the default`() =
        runTest {
            val savedState = SavedStateHandle(mapOf("albumSortField" to "Removed", "albumSortAscending" to false))
            val viewModel = AlbumListViewModel(library, savedState)
            subscribe(viewModel.state)

            assertEquals(AlbumSort(), viewModel.state.value.sort)
            assertEquals(true, viewModel.state.value.isLoaded)
        }

    @Test
    fun `continue listening shows the album of the track in the queue`() =
        runTest {
            val viewModel = HomeViewModel(library, player)
            subscribe(viewModel.state)
            assertNull(viewModel.state.value.continueAlbum)

            player.state.value = PlayerState(currentTrack = second)
            assertEquals(album, viewModel.state.value.continueAlbum)

            player.state.value = PlayerState(currentTrack = second.copy(albumId = null))
            assertNull(viewModel.state.value.continueAlbum)
        }

    @Test
    fun `home knows when the library is empty`() =
        runTest {
            val viewModel = HomeViewModel(FakeLibrary(emptyList(), emptyList()), player)
            subscribe(viewModel.state)

            assertEquals(false, viewModel.state.value.hasMusic)
        }

    private fun <T> TestScope.subscribe(state: StateFlow<T>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect {} }
    }

    private class FakeLibrary(
        tracks: List<Track>,
        albums: List<Album>,
    ) : LibraryRepository {
        private val tracks = MutableStateFlow(tracks)
        private val albums = MutableStateFlow(albums)

        override fun observeTracks(): Flow<List<Track>> = tracks

        override fun observeAlbums(): Flow<List<Album>> = albums

        override fun observeAlbumTracks(albumId: Long): Flow<List<Track>> =
            tracks.map { list -> list.filter { it.albumId == albumId } }

        override suspend fun tracksByIds(ids: List<Long>): List<Track> = tracks.value.filter { it.id in ids }

        override suspend fun scan() = Unit
    }
}
