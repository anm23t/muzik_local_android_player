package org.muzzzlo.player.feature.tageditor

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.model.Album
import org.muzzzlo.player.core.model.Artist
import org.muzzzlo.player.core.model.ArtistEdit
import org.muzzzlo.player.core.model.ArtistEditToken
import org.muzzzlo.player.core.model.ArtistProfileRepository
import org.muzzzlo.player.core.model.AudioFormat
import org.muzzzlo.player.core.model.EmbeddedPicture
import org.muzzzlo.player.core.model.FileTags
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.TagEdit
import org.muzzzlo.player.core.model.TagReadResult
import org.muzzzlo.player.core.model.TagWriteResult
import org.muzzzlo.player.core.model.Track
import org.muzzzlo.player.core.model.TrackTagRepository
import org.muzzzlo.player.core.model.TrackTags
import org.muzzzlo.player.core.tags.WritePermissionRequest

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistProfileViewModelTest {
    private val tagsOne =
        TrackTags(title = "T1", artists = listOf("Kanye West & JAY-Z"), albumArtists = listOf("Kanye West & JAY-Z"))
    private val tagsTwo = TrackTags(title = "T2", artists = listOf("Drake", "Kanye West & JAY-Z"))
    private val tagsThree = TrackTags(title = "T3", artists = listOf("Kanye West & JAY-Z"))
    private val repository = FakeTags(mapOf(1L to tagsOne, 2L to tagsTwo, 3L to tagsThree))
    private val profiles = FakeProfiles()
    private val library = FakeLibrary(listOf(track(1), track(2), track(3), track(4)))
    private val permissionRequests = mutableListOf<List<String>>()
    private var permissionAnswer: WritePermissionRequest = WritePermissionRequest.NotNeeded

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        ArtistProfileViewModel(
            tags = repository,
            library = library,
            profiles = profiles,
            permissions = { uris ->
                permissionRequests += uris
                permissionAnswer
            },
            savedState = SavedStateHandle(mapOf(PROFILE_ARTIST_ID_ARG to 5L)),
        )

    private fun ArtistProfileViewModel.save() {
        onSave()
        onConfirmWrite()
    }

    @Test
    fun `the editor starts with the name and no change`() {
        val state = viewModel().state.value
        assertEquals(listOf("Kanye West & JAY-Z"), state.values)
        assertEquals(0, state.trackCount)
        assertFalse(state.canSave)
    }

    @Test
    fun `a rename writes the new name into artists and album artists, keeping the other artists`() {
        val viewModel = viewModel()
        viewModel.onValuesChange(listOf("Ye & JAY-Z"))

        viewModel.save()

        assertEquals(
            listOf(
                1L to TagEdit.from(tagsOne).copy(artists = listOf("Ye & JAY-Z"), albumArtists = listOf("Ye & JAY-Z")),
                2L to TagEdit.from(tagsTwo).copy(artists = listOf("Drake", "Ye & JAY-Z")),
                3L to TagEdit.from(tagsThree).copy(artists = listOf("Ye & JAY-Z")),
            ),
            repository.writes,
        )
        assertEquals(listOf("commit"), profiles.calls.drop(1))
        assertTrue(viewModel.state.value.saved)
    }

    @Test
    fun `a split puts the parts in the place of the artist`() {
        val viewModel = viewModel()
        viewModel.onValuesChange(listOf("Kanye West", "JAY-Z"))

        viewModel.save()

        assertEquals(listOf("Drake", "Kanye West", "JAY-Z"), repository.writes[1].second.artists)
        assertNull(viewModel.state.value.savedArtistId)
    }

    @Test
    fun `typing the name of another artist says the tracks will be merged`() {
        library.artistIds["JAY-Z"] = 8L
        val viewModel = viewModel()

        viewModel.onValuesChange(listOf("JAY-Z"))

        assertEquals(listOf("JAY-Z"), viewModel.state.value.merging)
    }

    @Test
    fun `only files that change are counted and written`() {
        repository.extra[4L] = TrackTags(title = "T4", artists = listOf("Drake"))
        profiles.ids = listOf(1L, 4L)
        val viewModel = viewModel()
        viewModel.onValuesChange(listOf("Ye & JAY-Z"))

        assertEquals(1, viewModel.state.value.trackCount)
        viewModel.save()
        assertEquals(listOf(1L), repository.writes.map { it.first })
    }

    @Test
    fun `a failure restores the files written before it and rolls the database back`() {
        repository.results[2L] = TagWriteResult.WriteFailed
        val viewModel = viewModel()
        viewModel.onValuesChange(listOf("Ye & JAY-Z"))

        viewModel.save()

        assertEquals(listOf(1L, 2L, 1L), repository.writes.map { it.first })
        assertEquals(TagEdit.from(tagsOne), repository.writes.last().second)
        assertEquals(
            ProfileDialog.Failed(
                AlbumWriteFailure("T2", TagWriteResult.WriteFailed, restored = true, notRestored = emptyList()),
            ),
            viewModel.state.value.dialog,
        )
        assertEquals(listOf("rollback"), profiles.calls.drop(1))
        assertFalse(viewModel.state.value.saved)
    }

    @Test
    fun `a refused permission writes nothing and rolls back`() {
        permissionAnswer = WritePermissionRequest.RuntimePermission("p")
        val viewModel = viewModel()
        viewModel.onValuesChange(listOf("Ye & JAY-Z"))
        viewModel.save()

        viewModel.onPermissionResult(granted = false)

        assertEquals(emptyList<Pair<Long, TagEdit>>(), repository.writes)
        assertEquals(listOf(listOf("content://media/1", "content://media/2", "content://media/3")), permissionRequests)
        assertEquals(listOf("rollback"), profiles.calls.drop(1))
    }

    @Test
    fun `back asks before dropping changes`() {
        val viewModel = viewModel()
        viewModel.onBack()
        assertTrue(viewModel.state.value.done)

        val edited = viewModel()
        edited.onValuesChange(listOf("Other"))
        edited.onBack()
        assertEquals(ProfileDialog.Discard, edited.state.value.dialog)
        assertFalse(edited.state.value.done)
    }

    @Test
    fun `an artist that is gone says so`() {
        profiles.name = null
        assertTrue(viewModel().state.value.notFound)
    }

    private fun track(id: Long) = Track(id, "content://media/$id", "T$id", emptyList(), null, null, 1_000, null)

    private class FakeProfiles : ArtistProfileRepository {
        var name: String? = "Kanye West & JAY-Z"
        val calls = mutableListOf<String>()

        override suspend fun artistName(artistId: Long): String? = name

        var ids = listOf(1L, 2L, 3L)

        override suspend fun affectedTrackIds(artistId: Long): List<Long> = ids

        override suspend fun begin(
            artistId: Long,
            edit: ArtistEdit,
        ): ArtistEditToken {
            calls += "begin"
            return ArtistEditToken(artistId, edit, emptyList())
        }

        override suspend fun commit(token: ArtistEditToken) {
            calls += "commit"
        }

        override suspend fun rollback(token: ArtistEditToken) {
            calls += "rollback"
        }
    }

    private class FakeTags(private val tags: Map<Long, TrackTags>) : TrackTagRepository {
        val writes = mutableListOf<Pair<Long, TagEdit>>()
        val results = mutableMapOf<Long, TagWriteResult>()
        val extra = mutableMapOf<Long, TrackTags>()

        override suspend fun readTags(trackId: Long): TagReadResult =
            TagReadResult.Success(FileTags(AudioFormat.MP3, 4, extra[trackId] ?: tags.getValue(trackId), null))

        override suspend fun readCover(trackId: Long): EmbeddedPicture? = null

        override suspend fun writeTags(
            trackId: Long,
            edit: TagEdit,
        ): TagWriteResult {
            writes += trackId to edit
            return results.remove(trackId) ?: TagWriteResult.Success
        }
    }

    private class FakeLibrary(private val tracks: List<Track>) : LibraryRepository {
        val artistIds = mutableMapOf<String, Long>()

        override fun observeTracks(): Flow<List<Track>> = emptyFlow()

        override fun observeAlbums(): Flow<List<Album>> = emptyFlow()

        override fun observeAlbumTracks(albumId: Long): Flow<List<Track>> = emptyFlow()

        override fun observeArtists(): Flow<List<Artist>> = emptyFlow()

        override fun observeArtistTracks(artistId: Long): Flow<List<Track>> = emptyFlow()

        override fun observeArtistAlbums(artistId: Long): Flow<List<Album>> = emptyFlow()

        override suspend fun artistId(name: String): Long? = artistIds[name]

        override suspend fun setArtistPhoto(
            artistId: Long,
            sourceUri: String,
        ) = Unit

        override suspend fun removeArtistPhoto(artistId: Long) = Unit

        override suspend fun tracksByIds(ids: List<Long>): List<Track> = tracks.filter { it.id in ids }

        override suspend fun scan() = Unit
    }
}
