package org.muzzzlo.player.feature.tageditor

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.muzzzlo.player.core.model.Album
import org.muzzzlo.player.core.model.AppLanguage
import org.muzzzlo.player.core.model.AppSettings
import org.muzzzlo.player.core.model.Artist
import org.muzzzlo.player.core.model.AudioFormat
import org.muzzzlo.player.core.model.CoverEdit
import org.muzzzlo.player.core.model.EmbeddedPicture
import org.muzzzlo.player.core.model.FileTags
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.SettingsRepository
import org.muzzzlo.player.core.model.TagEdit
import org.muzzzlo.player.core.model.TagReadResult
import org.muzzzlo.player.core.model.TagWriteResult
import org.muzzzlo.player.core.model.Track
import org.muzzzlo.player.core.model.TrackTagRepository
import org.muzzzlo.player.core.model.TrackTags
import org.muzzzlo.player.core.tags.WritePermissionRequest

@OptIn(ExperimentalCoroutinesApi::class)
class TagEditorViewModelTest {
    private val track = Track(TRACK_ID, "content://media/7", "Song", listOf("A"), null, null, 1_000, 1)
    private val cover = EmbeddedPicture(byteArrayOf(1, 2), "image/jpeg", EmbeddedPicture.TYPE_FRONT_COVER)
    private val picked = EmbeddedPicture(byteArrayOf(3), "image/png", EmbeddedPicture.TYPE_FRONT_COVER)
    private val tags = TrackTags(title = "Song", artists = listOf("A"), hasCover = true)

    private val repository = FakeTrackTags(TagReadResult.Success(FileTags(AudioFormat.MP3, 4, tags, null)), cover)
    private val pictures = PictureReader { uri -> if (uri == PICKED_URI) picked else null }
    private var permissionRequest: WritePermissionRequest = WritePermissionRequest.RuntimePermission(PERMISSION)
    private val permissionUris = mutableListOf<String>()
    private val settings = FakeSettings()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        TagEditorViewModel(
            tags = repository,
            library = FakeLibrary(track),
            pictures = pictures,
            permissions = { uris ->
                permissionUris += uris
                permissionRequest
            },
            settings = settings,
            savedState = SavedStateHandle(mapOf(TRACK_ID_ARG to TRACK_ID)),
        )

    private fun TagEditorViewModel.retitle(title: String) = onDraftChange(state.value.draft!!.copy(title = title))

    @Test
    fun `the editor shows the tags and the cover of the file`() {
        val state = viewModel().state.value

        assertEquals("Song", state.trackTitle)
        assertEquals(TagDraft.from(tags), state.draft)
        assertEquals(cover, state.cover)
        assertFalse(state.canSave)
    }

    @Test
    fun `a file that cannot be read shows why`() {
        repository.read = TagReadResult.Quarantined
        val state = viewModel().state.value

        assertEquals(LoadError.Quarantined, state.loadError)
        assertFalse(state.isLoading)
    }

    @Test
    fun `nothing is written before the user confirms`() {
        val viewModel = viewModel()
        viewModel.retitle("New")

        viewModel.onSave()

        assertEquals(EditorDialog.ConfirmWrite, viewModel.state.value.dialog)
        assertEquals(listOf(TagChange(TagField.Title, "Song", "New")), viewModel.state.value.changes)
        assertEquals(emptyList<TagEdit>(), repository.writes)

        viewModel.onDismissDialog()
        assertEquals(emptyList<TagEdit>(), repository.writes)
    }

    @Test
    fun `with the confirmation switched off save writes at once`() {
        settings.state.value = AppSettings(confirmBeforeWrite = false)
        val viewModel = viewModel()
        viewModel.retitle("New")

        viewModel.onSave()

        assertEquals(listOf(TagEdit.from(tags).copy(title = "New")), repository.writes)
        assertNull(viewModel.state.value.dialog)
        assertTrue(viewModel.state.value.done)
    }

    @Test
    fun `a confirmed write goes into the file and closes the editor`() {
        val viewModel = viewModel()
        viewModel.retitle("New")

        viewModel.onSave()
        viewModel.onConfirmWrite()

        assertEquals(listOf(TagEdit.from(tags).copy(title = "New")), repository.writes)
        assertTrue(viewModel.state.value.done)
    }

    @Test
    fun `save is not offered without changes or with an invalid field`() {
        val viewModel = viewModel()
        viewModel.onSave()
        assertNull(viewModel.state.value.dialog)

        viewModel.onDraftChange(viewModel.state.value.draft!!.copy(title = "New", year = "20x1"))
        viewModel.onSave()

        assertFalse(viewModel.state.value.canSave)
        assertNull(viewModel.state.value.dialog)
    }

    @Test
    fun `a missing permission is asked for and the write is retried after allow`() {
        repository.results += TagWriteResult.NeedsPermission
        val viewModel = viewModel()
        viewModel.retitle("New")
        viewModel.onSave()
        viewModel.onConfirmWrite()

        assertEquals(WritePermissionRequest.RuntimePermission(PERMISSION), viewModel.state.value.permissionRequest)
        assertEquals(listOf(track.uri), permissionUris)

        viewModel.onPermissionResult(granted = true)

        assertNull(viewModel.state.value.permissionRequest)
        assertEquals(2, repository.writes.size)
        assertTrue(viewModel.state.value.done)
    }

    @Test
    fun `a refused permission is reported and nothing is retried`() {
        repository.results += TagWriteResult.NeedsPermission
        val viewModel = viewModel()
        viewModel.retitle("New")
        viewModel.onSave()
        viewModel.onConfirmWrite()

        viewModel.onPermissionResult(granted = false)

        assertEquals(EditorDialog.Problem(WriteProblem.PermissionDenied), viewModel.state.value.dialog)
        assertEquals(1, repository.writes.size)
    }

    @Test
    fun `a second refusal after allow is reported instead of asking again`() {
        repository.results += listOf(TagWriteResult.NeedsPermission, TagWriteResult.NeedsPermission)
        val viewModel = viewModel()
        viewModel.retitle("New")
        viewModel.onSave()
        viewModel.onConfirmWrite()

        viewModel.onPermissionResult(granted = true)

        assertEquals(EditorDialog.Problem(WriteProblem.PermissionDenied), viewModel.state.value.dialog)
        assertNull(viewModel.state.value.permissionRequest)
        assertEquals(listOf(track.uri), permissionUris)
    }

    @ParameterizedTest
    @MethodSource("failures")
    fun `a failed write is shown and the draft is kept`(failure: TagWriteResult) {
        repository.results += failure
        val viewModel = viewModel()
        viewModel.retitle("New")
        viewModel.onSave()
        viewModel.onConfirmWrite()

        val state = viewModel.state.value
        assertEquals(EditorDialog.Problem(WriteProblem.Failed(failure)), state.dialog)
        assertEquals("New", state.draft?.title)
        assertFalse(state.writing)
        assertFalse(state.done)
    }

    @Test
    fun `a picked image becomes the new cover`() {
        val viewModel = viewModel()
        viewModel.onCoverClicked()
        assertEquals(EditorDialog.CoverMenu, viewModel.state.value.dialog)

        viewModel.onCoverPicked(PICKED_URI)

        val state = viewModel.state.value
        assertNull(state.dialog)
        assertEquals(picked, state.cover)
        assertEquals(CoverEdit.Replace(picked), state.draft?.cover)
        assertEquals(listOf(TagChange(TagField.Cover, "", "")), state.changes)
    }

    @Test
    fun `an image that cannot be read keeps the cover`() {
        val viewModel = viewModel()
        viewModel.onCoverPicked("content://broken")

        assertEquals(EditorDialog.Problem(WriteProblem.CoverUnreadable), viewModel.state.value.dialog)
        assertEquals(cover, viewModel.state.value.cover)
    }

    @Test
    fun `a removed cover leaves the card empty`() {
        val viewModel = viewModel()
        viewModel.onCoverRemoved()

        assertNull(viewModel.state.value.cover)
        assertEquals(CoverEdit.Remove, viewModel.state.value.draft?.cover)
    }

    @Test
    fun `back without changes closes and back with changes asks first`() {
        val untouched = viewModel()
        untouched.onBack()
        assertTrue(untouched.state.value.done)

        val edited = viewModel()
        edited.retitle("New")
        edited.onBack()
        assertEquals(EditorDialog.Discard, edited.state.value.dialog)
        assertFalse(edited.state.value.done)

        edited.onDiscard()
        assertTrue(edited.state.value.done)
        assertEquals(emptyList<TagEdit>(), repository.writes)
    }

    @Test
    fun `back with a field that is not a number yet asks first`() {
        val viewModel = viewModel()
        viewModel.onDraftChange(viewModel.state.value.draft!!.copy(year = "2o24"))

        viewModel.onBack()

        assertEquals(EditorDialog.Discard, viewModel.state.value.dialog)
    }

    private class FakeTrackTags(
        var read: TagReadResult,
        val cover: EmbeddedPicture?,
    ) : TrackTagRepository {
        val writes = mutableListOf<TagEdit>()

        /** Results of the next writes in order; [TagWriteResult.Success] once they run out. */
        val results = ArrayDeque<TagWriteResult>()

        override suspend fun readTags(trackId: Long) = read

        override suspend fun readCover(trackId: Long) = cover

        override suspend fun writeTags(
            trackId: Long,
            edit: TagEdit,
        ): TagWriteResult {
            writes += edit
            return results.removeFirstOrNull() ?: TagWriteResult.Success
        }

        operator fun ArrayDeque<TagWriteResult>.plusAssign(items: List<TagWriteResult>) {
            addAll(items)
        }
    }

    private class FakeLibrary(private val track: Track) : LibraryRepository {
        override fun observeTracks(): Flow<List<Track>> = emptyFlow()

        override fun observeAlbums(): Flow<List<Album>> = emptyFlow()

        override fun observeAlbumTracks(albumId: Long): Flow<List<Track>> = emptyFlow()

        override fun observeArtists(): Flow<List<Artist>> = emptyFlow()

        override fun observeArtistTracks(artistId: Long): Flow<List<Track>> = emptyFlow()

        override fun observeArtistAlbums(artistId: Long): Flow<List<Album>> = emptyFlow()

        override suspend fun artistId(name: String): Long? = null

        override suspend fun setArtistPhoto(
            artistId: Long,
            sourceUri: String,
        ) = Unit

        override suspend fun removeArtistPhoto(artistId: Long) = Unit

        override suspend fun tracksByIds(ids: List<Long>): List<Track> = listOf(track).filter { it.id in ids }

        override suspend fun scan() = Unit
    }

    companion object {
        private const val TRACK_ID = 7L
        private const val PICKED_URI = "content://picked"
        private const val PERMISSION = "android.permission.WRITE_EXTERNAL_STORAGE"

        @JvmStatic
        fun failures(): List<TagWriteResult> =
            listOf(
                TagWriteResult.NotEnoughSpace(10),
                TagWriteResult.Unreadable,
                TagWriteResult.Quarantined,
                TagWriteResult.WriteFailed,
                TagWriteResult.VerificationFailed("audio"),
                TagWriteResult.FileChanged,
                TagWriteResult.ReplaceFailed(restored = true),
                TagWriteResult.ReplaceFailed(restored = false),
            )
    }
}

private class FakeSettings : SettingsRepository {
    val state = MutableStateFlow(AppSettings())
    override val settings: Flow<AppSettings> = state

    override suspend fun setFolderIncluded(
        relativePath: String,
        included: Boolean,
    ) = Unit

    override suspend fun setMinTrackSeconds(seconds: Int) = Unit

    override suspend fun setId3v2VersionForNewTags(version: Int) = Unit

    override suspend fun setConfirmBeforeWrite(enabled: Boolean) = Unit

    override suspend fun setLanguage(language: AppLanguage) = Unit

    override suspend fun setDynamicAccent(enabled: Boolean) = Unit
}
