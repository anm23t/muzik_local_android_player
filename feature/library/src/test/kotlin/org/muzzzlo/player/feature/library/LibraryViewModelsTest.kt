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
import org.muzzzlo.player.core.designsystem.component.ArtistChoice
import org.muzzzlo.player.core.designsystem.component.TrackAction
import org.muzzzlo.player.core.model.Album
import org.muzzzlo.player.core.model.Artist
import org.muzzzlo.player.core.model.ArtistCheckRepository
import org.muzzzlo.player.core.model.ArtistDiscography
import org.muzzzlo.player.core.model.ArtistRef
import org.muzzzlo.player.core.model.CompoundNameProblem
import org.muzzzlo.player.core.model.CompoundNames
import org.muzzzlo.player.core.model.CompoundRepository
import org.muzzzlo.player.core.model.CompoundRule
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
    fun `the track menu gets the artists of the track in tag order with their photos`() =
        runTest {
            val lib = FakeLibrary(listOf(second), listOf(album), listOf(anna.copy(photoPath = "/p/a.jpg"), bob))
            val viewModel = TracksViewModel(lib, player, SavedStateHandle())

            assertEquals(
                listOf(ArtistChoice(7, "A", "/p/a.jpg"), ArtistChoice(8, "B", null)),
                viewModel.artistChoices(second.id),
            )
            assertEquals(emptyList<ArtistChoice>(), viewModel.artistChoices(999))
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
    fun `the album card gives each album artist their photo and null when there is none`() =
        runTest {
            val twoArtists = Album(12, "Duo", listOf("A", "B", "Nobody"), 2020, 1, 10)
            val lib = FakeLibrary(listOf(first), listOf(twoArtists), listOf(anna.copy(photoPath = "/p/a.jpg"), bob))
            val viewModel = AlbumViewModel(lib, player, SavedStateHandle(mapOf(ALBUM_ID_ARG to twoArtists.id)))
            subscribe(viewModel.state)

            assertEquals(listOf("/p/a.jpg", null, null), viewModel.state.value.artistPhotos)
        }

    @Test
    fun `an album without artists has no photos`() =
        assertEquals(
            emptyList<String?>(),
            photosOf(emptyList(), listOf(anna)),
        )

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
            val viewModel = ArtistListViewModel(library, FakeCompounds(), SavedStateHandle())
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
            assertEquals(ArtistDiscography(albums = listOf(album)), viewModel.state.value.discography)
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

    @Test
    fun `the check finds doubled artists and merges the group into the chosen spelling`() =
        runTest {
            val doubled =
                FakeLibrary(emptyList(), emptyList(), listOf(Artist(1, "Beyoncé", 2), Artist(2, "Beyonce", 5), bob))
            val check = FakeCheck()
            val viewModel = ArtistCheckViewModel(doubled, check)
            subscribe(viewModel.state)

            val group = viewModel.state.value.groups.single()
            assertEquals(2L, group.keepId)

            viewModel.onKeepChosen(0, 1)
            viewModel.onApply(0)

            assertEquals(listOf(1L to listOf(2L)), check.merges)
            assertEquals(emptyList<CheckGroup>(), viewModel.state.value.groups)
            assertEquals(1, viewModel.state.value.appliedCount)
        }

    @Test
    fun `skipping a group merges nothing and a clean library has no groups`() =
        runTest {
            val doubled = FakeLibrary(emptyList(), emptyList(), listOf(Artist(1, "Pink", 1), Artist(2, "P!nk", 1)))
            val check = FakeCheck()
            val viewModel = ArtistCheckViewModel(doubled, check)
            subscribe(viewModel.state)
            viewModel.onSkip(0)
            assertEquals(emptyList<CheckGroup>(), viewModel.state.value.groups)
            assertEquals(emptyList<Pair<Long, List<Long>>>(), check.merges)

            val clean = ArtistCheckViewModel(library, check)
            subscribe(clean.state)
            assertEquals(true, clean.state.value.isLoaded)
            assertEquals(emptyList<CheckGroup>(), clean.state.value.groups)
        }

    @Test
    fun `ticked artists keep their order and unticking removes one`() =
        runTest {
            val viewModel = ArtistListViewModel(library, FakeCompounds(), SavedStateHandle())
            subscribe(viewModel.state)

            viewModel.onSelectionToggled()
            viewModel.onArtistTicked(bob.id)
            viewModel.onArtistTicked(anna.id)
            assertEquals(listOf(bob.id, anna.id), viewModel.state.value.selectedIds)

            viewModel.onArtistTicked(bob.id)
            assertEquals(listOf(anna.id), viewModel.state.value.selectedIds)

            viewModel.onSelectionToggled()
            assertEquals(false, viewModel.state.value.selecting)
            assertEquals(emptyList<Long>(), viewModel.state.value.selectedIds)
        }

    @Test
    fun `the remember dialog needs two artists and suggests their names in the ticked order`() =
        runTest {
            val viewModel = ArtistListViewModel(library, FakeCompounds(), SavedStateHandle())
            subscribe(viewModel.state)
            viewModel.onSelectionToggled()
            viewModel.onArtistTicked(bob.id)

            viewModel.onRememberClicked()
            assertNull(viewModel.state.value.remember)

            viewModel.onArtistTicked(anna.id)
            viewModel.onRememberClicked()
            assertEquals(listOf("B", "A"), viewModel.state.value.remember?.parts)
            assertEquals("B, A", viewModel.state.value.remember?.name)

            viewModel.onPartMoved(0, 1)
            assertEquals(listOf("A", "B"), viewModel.state.value.remember?.parts)
            assertEquals("A, B", viewModel.state.value.remember?.name)

            viewModel.onPartMoved(0, -1)
            assertEquals(listOf("A", "B"), viewModel.state.value.remember?.parts)
        }

    @Test
    fun `a wrong name is refused and a right one is saved and ends the choice mode`() =
        runTest {
            val compounds = FakeCompounds()
            val viewModel = ArtistListViewModel(library, compounds, SavedStateHandle())
            subscribe(viewModel.state)
            viewModel.onSelectionToggled()
            viewModel.onArtistTicked(anna.id)
            viewModel.onArtistTicked(bob.id)
            viewModel.onRememberClicked()

            viewModel.onNameChanged("A B")
            viewModel.onRememberConfirmed()
            assertEquals(CompoundNameProblem.TooShort, viewModel.state.value.remember?.problem)

            viewModel.onNameChanged("B, A")
            viewModel.onRememberConfirmed()
            assertEquals(CompoundNameProblem.NotSelectedParts, viewModel.state.value.remember?.problem)
            assertEquals(emptyList<String>(), compounds.remembered)

            viewModel.onNameChanged("A, B")
            viewModel.onRememberConfirmed()
            assertEquals(listOf("A, B"), compounds.remembered)
            assertNull(viewModel.state.value.remember)
            assertEquals(false, viewModel.state.value.selecting)
        }

    @Test
    fun `a name that is already a rule is reported and keeps the dialog open`() =
        runTest {
            val compounds = FakeCompounds(result = -1)
            val viewModel = ArtistListViewModel(library, compounds, SavedStateHandle())
            subscribe(viewModel.state)
            viewModel.onSelectionToggled()
            viewModel.onArtistTicked(anna.id)
            viewModel.onArtistTicked(bob.id)
            viewModel.onRememberClicked()

            viewModel.onRememberConfirmed()

            assertEquals(true, viewModel.state.value.remember?.duplicate)
            assertEquals(true, viewModel.state.value.selecting)
        }

    private class FakeCompounds(
        private val result: Int = 0,
        private val affected: Int = 0,
    ) : CompoundRepository {
        val remembered = mutableListOf<String>()
        val removed = mutableListOf<Long>()

        override fun observeRules(): Flow<List<CompoundRule>> = MutableStateFlow(emptyList())

        override suspend fun compoundNames() = CompoundNames.None

        override suspend fun syncBuiltIn() = Unit

        override suspend fun remember(name: String): Int {
            if (result >= 0) remembered += name
            return result
        }

        override suspend fun remove(id: Long): Int {
            removed += id
            return 0
        }

        override suspend fun countAffectedBy(id: Long) = affected

        override suspend fun enable(id: Long) = 0
    }

    private class FakeCheck : ArtistCheckRepository {
        val merges = mutableListOf<Pair<Long, List<Long>>>()

        override suspend fun merge(
            keepId: Long,
            variantIds: List<Long>,
        ) {
            merges += keepId to variantIds
        }
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

        /** Albums credited to the artist go to "Albums", the rest of their albums to "Appears on". */
        override fun observeArtistAlbums(artistId: Long): Flow<ArtistDiscography> =
            combine(observeArtistTracks(artistId), albums, artists) { list, albums, artists ->
                val name = artists.firstOrNull { it.id == artistId }?.name
                val theirs = albums.filter { album -> list.any { it.albumId == album.id } }
                val (credited, others) = theirs.partition { name in it.artists }
                ArtistDiscography(albums = credited, appearsOn = others)
            }

        override suspend fun artistId(name: String): Long? = artists.value.firstOrNull { it.name == name }?.id

        override suspend fun artistsOfTrack(trackId: Long): List<ArtistRef> =
            tracks.value.firstOrNull { it.id == trackId }?.artists.orEmpty().mapNotNull { name ->
                artists.value.firstOrNull { it.name == name }?.let { ArtistRef(it.id, it.name, it.photoPath) }
            }

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
