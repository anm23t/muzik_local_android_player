package org.muzzzlo.player.feature.library

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
import org.muzzzlo.player.core.model.Artist
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.PlayerState
import org.muzzzlo.player.core.model.Track

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelsTest {
    private val first = Track(1, "content://1", "First", listOf("A"), 10, "Album", 1_000, 1)
    private val second = Track(2, "content://2", "Second", listOf("A", "B"), 10, "Album", 1_000, 2)
    private val album = Album(10, "Album", listOf("A"), 2010, 2, 100)
    private val older = Album(11, "Older", listOf("C"), 1999, 1, 50)

    private val anna = Artist(7, "A", 2)
    private val bob = Artist(8, "B", 1)

    private val library = FakeLibrary(listOf(first, second), listOf(album, older), listOf(anna, bob))
    private val player = FakePlayerController()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @ParameterizedTest
    @EnumSource(TrackAction::class)
    fun `only queue actions reach the player`(action: TrackAction) {
        TracksViewModel(library, player, SavedStateHandle()).onTrackAction(first, action)

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
            val viewModel = TracksViewModel(library, player, SavedStateHandle())
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
            val savedState = SavedStateHandle(mapOf("albumSort.field" to "Removed", "albumSort.ascending" to false))
            val viewModel = AlbumListViewModel(library, savedState)
            subscribe(viewModel.state)

            assertEquals(DefaultAlbumSort, viewModel.state.value.sort)
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
    fun `the artist list follows the chosen sort`() =
        runTest {
            val viewModel = ArtistListViewModel(library, SavedStateHandle())
            subscribe(viewModel.state)
            assertEquals(listOf(anna, bob), viewModel.state.value.artists)

            viewModel.onSortChosen(ArtistSortField.Name)

            assertEquals(listOf(bob, anna), viewModel.state.value.artists)
        }

    @Test
    fun `the artist card shows the artist and their albums`() =
        runTest {
            val viewModel = ArtistViewModel(library, SavedStateHandle(mapOf(ARTIST_ID_ARG to anna.id)))
            subscribe(viewModel.state)

            assertEquals(anna, viewModel.state.value.artist)
            assertEquals(listOf(album), viewModel.state.value.albums)
        }

    @Test
    fun `the artist card opens by name`() =
        runTest {
            val viewModel =
                ArtistViewModel(library, SavedStateHandle(mapOf(ARTIST_ID_ARG to NO_ARTIST, ARTIST_NAME_ARG to "B")))
            subscribe(viewModel.state)

            assertEquals(bob, viewModel.state.value.artist)
        }

    @Test
    fun `an unknown name shows no artist once loaded`() =
        runTest {
            val viewModel = ArtistViewModel(library, SavedStateHandle(mapOf(ARTIST_NAME_ARG to "Nobody")))
            subscribe(viewModel.state)

            assertNull(viewModel.state.value.artist)
            assertEquals(true, viewModel.state.value.isLoaded)
        }

    @Test
    fun `a picked photo goes to the library and a broken one is ignored`() =
        runTest {
            val viewModel = ArtistViewModel(library, SavedStateHandle(mapOf(ARTIST_ID_ARG to anna.id)))
            subscribe(viewModel.state)

            viewModel.onPhotoPicked("content://media/picker/1")
            viewModel.onPhotoPicked(FakeLibrary.BROKEN_PICTURE)
            viewModel.onPhotoRemoved()

            assertEquals(listOf("set 7 content://media/picker/1", "remove 7"), library.photoCalls)
        }

    @Test
    fun `the songs of an artist are titled with their name`() =
        runTest {
            val viewModel = TracksViewModel(library, player, SavedStateHandle(mapOf(ARTIST_ID_ARG to anna.id)))
            subscribe(viewModel.state)

            assertEquals(listOf(first, second), viewModel.state.value.tracks)
            assertEquals("A", viewModel.state.value.artistName)
        }

    @Test
    fun `without an artist the list has every track`() =
        runTest {
            val viewModel = TracksViewModel(library, player, SavedStateHandle(mapOf(ARTIST_ID_ARG to NO_ARTIST)))
            subscribe(viewModel.state)

            assertEquals(listOf(first, second), viewModel.state.value.tracks)
            assertNull(viewModel.state.value.artistName)
        }

    @Test
    fun `home knows when the library is empty`() =
        runTest {
            val viewModel = HomeViewModel(FakeLibrary(emptyList(), emptyList(), emptyList()), player)
            subscribe(viewModel.state)

            assertEquals(false, viewModel.state.value.hasMusic)
        }

    private fun <T> TestScope.subscribe(state: StateFlow<T>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect {} }
    }

    private class FakeLibrary(
        tracks: List<Track>,
        albums: List<Album>,
        artists: List<Artist>,
    ) : LibraryRepository {
        private val tracks = MutableStateFlow(tracks)
        private val albums = MutableStateFlow(albums)
        private val artists = MutableStateFlow(artists)
        val photoCalls = mutableListOf<String>()

        override fun observeArtists(): Flow<List<Artist>> = artists

        override fun observeArtistTracks(artistId: Long): Flow<List<Track>> =
            combine(tracks, artists) { list, artists ->
                val name = artists.firstOrNull { it.id == artistId }?.name
                list.filter { it.artists.contains(name) }
            }

        override fun observeArtistAlbums(artistId: Long): Flow<List<Album>> =
            observeArtistTracks(artistId).combine(albums) { list, albums ->
                albums.filter { album -> list.any { it.albumId == album.id } }
            }

        override suspend fun artistId(name: String): Long? = artists.value.firstOrNull { it.name == name }?.id

        override suspend fun setArtistPhoto(
            artistId: Long,
            sourceUri: String,
        ) {
            if (sourceUri == BROKEN_PICTURE) error("Not an image")
            photoCalls += "set $artistId $sourceUri"
        }

        override suspend fun removeArtistPhoto(artistId: Long) {
            photoCalls += "remove $artistId"
        }

        override fun observeTracks(): Flow<List<Track>> = tracks

        override fun observeAlbums(): Flow<List<Album>> = albums

        override fun observeAlbumTracks(albumId: Long): Flow<List<Track>> =
            tracks.map { list -> list.filter { it.albumId == albumId } }

        override suspend fun tracksByIds(ids: List<Long>): List<Track> = tracks.value.filter { it.id in ids }

        override suspend fun scan() = Unit

        companion object {
            const val BROKEN_PICTURE = "content://broken"
        }
    }
}
