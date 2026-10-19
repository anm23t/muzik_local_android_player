package org.muzzzlo.player.feature.tageditor

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
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
import org.muzzzlo.player.core.model.AudioFormat
import org.muzzzlo.player.core.model.CoverEdit
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
class BatchTagEditorViewModelTest {
    private val one = track(1)
    private val two = track(2)
    private val three = track(3)
    private val tagsOne = TrackTags(title = "T1", album = "Album", year = 2020, trackNumber = 1)
    private val tagsTwo = TrackTags(title = "T2", album = "Album", year = 2021, trackNumber = 2, hasCover = true)
    private val tagsThree = TrackTags(title = "T3", album = "Album", year = 2021, trackNumber = 3)
    private val oldCover = EmbeddedPicture(byteArrayOf(1), "image/jpeg", EmbeddedPicture.TYPE_FRONT_COVER)
    private val newCover = EmbeddedPicture(byteArrayOf(2), "image/png", EmbeddedPicture.TYPE_FRONT_COVER)

    private val repository =
        FakeTags(mapOf(1L to tagsOne, 2L to tagsTwo, 3L to tagsThree), covers = mapOf(2L to oldCover))
    private val permissionRequests = mutableListOf<List<String>>()
    private var permissionAnswer: WritePermissionRequest = WritePermissionRequest.NotNeeded

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(tracks: List<Track> = listOf(one, two, three)) =
        BatchTagEditorViewModel(
            tags = repository,
            library = FakeLibrary(tracks),
            permissions = { uris ->
                permissionRequests += uris
                permissionAnswer
            },
            pictures = { newCover },
            savedState = SavedStateHandle(mapOf(BATCH_ALBUM_ID_ARG to 9L)),
        )

    private fun BatchTagEditorViewModel.setAlbum(name: String) {
        val draft = state.value.draft!!
        onDraftChange(draft.copy(album = draft.album.edit(name)))
    }

    private fun BatchTagEditorViewModel.save() {
        onSave()
        onConfirmWrite()
    }

    @Test
    fun `the editor shows what the tracks share and what differs`() {
        val state = viewModel().state.value

        assertEquals("Album", state.albumTitle)
        assertEquals(3, state.trackCount)
        assertEquals("Album", state.draft!!.album.value)
        assertFalse(state.draft!!.album.mixed)
        assertTrue(state.draft!!.year.mixed)
        assertFalse(state.canSave)
    }

    @Test
    fun `nothing is written before the user confirms`() {
        val viewModel = viewModel()
        viewModel.setAlbum("New")

        viewModel.onSave()

        assertEquals(BatchDialog.ConfirmWrite, viewModel.state.value.dialog)
        assertEquals(listOf(TagChange(TagField.Album, "", "New")), viewModel.state.value.changes)
        assertEquals(emptyList<Pair<Long, TagEdit>>(), repository.writes)
    }

    @Test
    fun `every track is written with its own tags and the new album`() {
        val viewModel = viewModel()
        viewModel.setAlbum("New")

        viewModel.save()

        assertEquals(
            listOf(
                1L to TagEdit.from(tagsOne).copy(album = "New"),
                2L to TagEdit.from(tagsTwo).copy(album = "New"),
                3L to TagEdit.from(tagsThree).copy(album = "New"),
            ),
            repository.writes,
        )
        assertTrue(viewModel.state.value.done)
        assertEquals(listOf(listOf(one.uri, two.uri, three.uri)), permissionRequests)
    }

    @Test
    fun `a track that already has the new value is not written`() {
        val viewModel = viewModel()
        val draft = viewModel.state.value.draft!!
        viewModel.onDraftChange(draft.copy(year = draft.year.edit("2021")))

        viewModel.save()

        assertEquals(listOf(1L), repository.writes.map { it.first })
        assertTrue(viewModel.state.value.done)
    }

    @Test
    fun `a failure restores the tracks written before it`() {
        repository.results[2L] = TagWriteResult.WriteFailed
        val viewModel = viewModel()
        viewModel.setAlbum("New")

        viewModel.save()

        assertEquals(
            listOf(
                1L to TagEdit.from(tagsOne).copy(album = "New"),
                2L to TagEdit.from(tagsTwo).copy(album = "New"),
                1L to TagEdit.from(tagsOne),
            ),
            repository.writes,
        )
        assertEquals(
            BatchDialog.Failed(
                AlbumWriteFailure("T2", TagWriteResult.WriteFailed, restored = true, notRestored = emptyList()),
            ),
            viewModel.state.value.dialog,
        )
        assertFalse(viewModel.state.value.done)
        assertNull(viewModel.state.value.progress)
    }

    @Test
    fun `tracks that cannot be restored are named`() {
        repository.results[3L] = TagWriteResult.WriteFailed
        repository.restoreFails += 2L
        val viewModel = viewModel()
        viewModel.setAlbum("New")

        viewModel.save()

        assertEquals(
            BatchDialog.Failed(
                AlbumWriteFailure("T3", TagWriteResult.WriteFailed, restored = false, notRestored = listOf("T2")),
            ),
            viewModel.state.value.dialog,
        )
        assertEquals(listOf(2L, 1L), repository.writes.drop(3).map { it.first })
    }

    @Test
    fun `a new cover is put back on restore`() {
        repository.results[3L] = TagWriteResult.WriteFailed
        val viewModel = viewModel()
        viewModel.onCoverPicked("content://picked")

        viewModel.save()

        val restoreOfTwo = repository.writes.last { it.first == 2L }.second
        val restoreOfOne = repository.writes.last { it.first == 1L }.second
        assertEquals(CoverEdit.Replace(oldCover), restoreOfTwo.cover)
        assertEquals(CoverEdit.Remove, restoreOfOne.cover)
    }

    @Test
    fun `the permission is asked once for all files and a refusal writes nothing`() {
        permissionAnswer = WritePermissionRequest.RuntimePermission("p")
        val viewModel = viewModel()
        viewModel.setAlbum("New")

        viewModel.save()

        assertEquals(permissionAnswer, viewModel.state.value.permissionRequest)
        assertEquals(listOf(listOf(one.uri, two.uri, three.uri)), permissionRequests)
        viewModel.onPermissionResult(granted = false)

        assertEquals(
            BatchDialog.Failed(AlbumWriteFailure(null, null, restored = true, notRestored = emptyList())),
            viewModel.state.value.dialog,
        )
        assertEquals(emptyList<Pair<Long, TagEdit>>(), repository.writes)
    }

    @Test
    fun `an allowed permission continues with the write`() {
        permissionAnswer = WritePermissionRequest.RuntimePermission("p")
        val viewModel = viewModel()
        viewModel.setAlbum("New")
        viewModel.save()

        viewModel.onPermissionResult(granted = true)

        assertEquals(3, repository.writes.size)
        assertTrue(viewModel.state.value.done)
    }

    @Test
    fun `a file that needs its own permission pauses the write and asks for the rest`() {
        repository.results[2L] = TagWriteResult.NeedsPermission
        val viewModel = viewModel()
        viewModel.setAlbum("New")
        viewModel.save()
        // Both requests are answered NotNeeded; the second one is made by the paused write.
        assertEquals(listOf(listOf(one.uri, two.uri, three.uri), listOf(two.uri, three.uri)), permissionRequests)

        assertEquals(listOf(1L, 2L, 2L, 3L), repository.writes.map { it.first })
        assertTrue(viewModel.state.value.done)
    }

    @Test
    fun `unreadable tracks are skipped and counted`() {
        repository.unreadable += 3L
        val viewModel = viewModel()
        viewModel.setAlbum("New")

        assertEquals(2, viewModel.state.value.trackCount)
        assertEquals(1, viewModel.state.value.skipped)
        viewModel.save()

        assertEquals(listOf(1L, 2L), repository.writes.map { it.first })
        assertTrue(viewModel.state.value.done)
    }

    @Test
    fun `an album without readable tracks shows why`() {
        repository.unreadable += listOf(1L, 2L, 3L)

        assertEquals(LoadError.Unreadable, viewModel().state.value.loadError)
        assertEquals(LoadError.Unreadable, viewModel(emptyList()).state.value.loadError)
    }

    @Test
    fun `back asks before dropping changes`() {
        val viewModel = viewModel()
        viewModel.onBack()
        assertTrue(viewModel.state.value.done)

        val edited = viewModel()
        edited.setAlbum("New")
        edited.onBack()

        assertEquals(BatchDialog.Discard, edited.state.value.dialog)
        assertFalse(edited.state.value.done)
        edited.onDiscard()
        assertTrue(edited.state.value.done)
        assertEquals(emptyList<Pair<Long, TagEdit>>(), repository.writes)
    }

    private fun track(id: Long) = Track(id, "content://media/$id", "T$id", emptyList(), 9, "Album", 1_000, id.toInt())

    private class FakeTags(
        private val tags: Map<Long, TrackTags>,
        private val covers: Map<Long, EmbeddedPicture>,
    ) : TrackTagRepository {
        val writes = mutableListOf<Pair<Long, TagEdit>>()
        val unreadable = mutableSetOf<Long>()

        /** The next write of the track fails with this result, once. */
        val results = mutableMapOf<Long, TagWriteResult>()

        /** Tracks whose restoring write (the second write of the same track) fails. */
        val restoreFails = mutableSetOf<Long>()

        override suspend fun readTags(trackId: Long): TagReadResult =
            if (trackId in unreadable) {
                TagReadResult.Unreadable
            } else {
                TagReadResult.Success(FileTags(AudioFormat.MP3, 4, tags.getValue(trackId), null))
            }

        override suspend fun readCover(trackId: Long): EmbeddedPicture? = covers[trackId]

        override suspend fun writeTags(
            trackId: Long,
            edit: TagEdit,
        ): TagWriteResult {
            val isRestore = writes.any { it.first == trackId }
            writes += trackId to edit
            if (isRestore && trackId in restoreFails) return TagWriteResult.WriteFailed
            return results.remove(trackId) ?: TagWriteResult.Success
        }
    }

    private class FakeLibrary(private val tracks: List<Track>) : LibraryRepository {
        override fun observeTracks(): Flow<List<Track>> = emptyFlow()

        override fun observeAlbums(): Flow<List<Album>> = emptyFlow()

        override fun observeAlbumTracks(albumId: Long): Flow<List<Track>> = flowOf(tracks)

        override fun observeArtists(): Flow<List<Artist>> = emptyFlow()

        override fun observeArtistTracks(artistId: Long): Flow<List<Track>> = emptyFlow()

        override fun observeArtistAlbums(artistId: Long): Flow<List<Album>> = emptyFlow()

        override suspend fun artistId(name: String): Long? = null

        override suspend fun setArtistPhoto(
            artistId: Long,
            sourceUri: String,
        ) = Unit

        override suspend fun removeArtistPhoto(artistId: Long) = Unit

        override suspend fun tracksByIds(ids: List<Long>): List<Track> = tracks.filter { it.id in ids }

        override suspend fun scan() = Unit
    }
}
