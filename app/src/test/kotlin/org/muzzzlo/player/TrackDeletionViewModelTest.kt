package org.muzzzlo.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.model.DeleteMethod
import org.muzzzlo.player.core.model.DeleteRequest
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.PlayerState
import org.muzzzlo.player.core.model.SystemDialogHandle
import org.muzzzlo.player.core.model.Track
import org.muzzzlo.player.core.model.TrackFileDeletion
import org.muzzzlo.player.core.model.WaveformPeaks
import org.muzzzlo.player.core.model.WaveformRepository
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class TrackDeletionViewModelTest {
    private val deletion = FakeDeletion()
    private val player = QueuePlayer()
    private val waveforms = FakeWaveforms()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = TrackDeletionViewModel(deletion, player, library(), waveforms)

    @Test
    fun `with the trash the system dialog comes at once and Allow deletes`() {
        deletion.method = DeleteMethod.TRASH
        deletion.next = DeleteRequest.SystemDialog(object : SystemDialogHandle {})
        val model = viewModel()

        model.onDeleteRequested(7, "Song")
        val asked = model.state.value as DeletionState.AskSystem
        assertEquals(7L, asked.trackId)
        assertEquals(listOf(7L), waveforms.forgotten)

        model.onSystemAnswer(true)
        assertEquals(listOf(7L), deletion.marked)
        assertEquals(DeletionState.Idle, model.state.value)
    }

    @Test
    fun `cancelling the system dialog keeps the track`() {
        deletion.next = DeleteRequest.SystemDialog(object : SystemDialogHandle {})
        val model = viewModel()

        model.onDeleteRequested(7, "Song")
        model.onSystemAnswer(false)

        assertEquals(emptyList<Long>(), deletion.deleted)
        assertEquals(emptyList<Long>(), deletion.marked)
        assertEquals(DeletionState.Idle, model.state.value)
    }

    @Test
    fun `without the trash the own warning comes first`() {
        deletion.method = DeleteMethod.CONSENT_DIALOG
        deletion.next = DeleteRequest.NotNeeded
        val model = viewModel()

        model.onDeleteRequested(7, "Song")
        assertEquals(DeletionState.Confirm(7, "Song"), model.state.value)
        assertEquals(emptyList<Long>(), deletion.requested)

        model.onConfirmed()
        assertEquals(listOf(7L), deletion.deleted)
        assertEquals(listOf(7L), deletion.marked)
    }

    @Test
    fun `dismissing the warning deletes nothing`() {
        deletion.method = DeleteMethod.WRITE_PERMISSION
        val model = viewModel()

        model.onDeleteRequested(7, "Song")
        model.onDismissed()
        model.onConfirmed()

        assertEquals(emptyList<Long>(), deletion.requested)
        assertEquals(DeletionState.Idle, model.state.value)
    }

    @Test
    fun `a refused permission keeps the file, a granted one deletes it`() {
        deletion.method = DeleteMethod.WRITE_PERMISSION
        deletion.next = DeleteRequest.RuntimePermission("android.permission.WRITE_EXTERNAL_STORAGE")
        val model = viewModel()

        model.onDeleteRequested(7, "Song")
        model.onConfirmed()
        model.onSystemAnswer(false)
        assertEquals(emptyList<Long>(), deletion.deleted)

        model.onDeleteRequested(7, "Song")
        model.onConfirmed()
        model.onSystemAnswer(true)
        assertEquals(listOf(7L), deletion.deleted)
    }

    @Test
    fun `a file that cannot be deleted is reported once and not marked`() {
        deletion.method = DeleteMethod.CONSENT_DIALOG
        deletion.next = DeleteRequest.NotNeeded
        deletion.deleteWorks = false
        val model = viewModel()

        model.onDeleteRequested(7, "Song")
        model.onConfirmed()
        assertEquals(DeletionState.Failed, model.state.value)
        assertEquals(emptyList<Long>(), deletion.marked)

        model.onFailureShown()
        assertEquals(DeletionState.Idle, model.state.value)
    }

    @Test
    fun `a track that is gone already needs nothing`() {
        deletion.next = DeleteRequest.Gone
        val model = viewModel()

        model.onDeleteRequested(7, "Song")

        assertEquals(DeletionState.Idle, model.state.value)
        assertEquals(emptyList<Long>(), deletion.marked)
    }

    @Test
    fun `the deleted track leaves every place of the queue`() {
        player.state.value = PlayerState(queue = listOf(track(1), track(7), track(2), track(7)), currentIndex = 1)
        deletion.next = DeleteRequest.SystemDialog(object : SystemDialogHandle {})
        val model = viewModel()

        model.onDeleteRequested(7, "Song")
        model.onSystemAnswer(true)

        assertEquals(listOf(3, 1), player.removed)
    }

    private fun track(id: Long) = Track(id, "content://media/$id", "T$id", emptyList(), null, null, 1000, null)

    /** A library that knows every track by its id; nothing else is called. */
    private fun library(): LibraryRepository =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(LibraryRepository::class.java)) { _, method, args ->
            check(method.name == "tracksByIds") { "unexpected call to ${method.name}" }
            (args[0] as List<*>).map { track(it as Long) }
        } as LibraryRepository

    private class FakeDeletion : TrackFileDeletion {
        override var method = DeleteMethod.TRASH
        var next: DeleteRequest = DeleteRequest.NotNeeded
        var deleteWorks = true
        val requested = mutableListOf<Long>()
        val deleted = mutableListOf<Long>()
        val marked = mutableListOf<Long>()

        override suspend fun request(trackId: Long): DeleteRequest = next.also { requested += trackId }

        override suspend fun delete(trackId: Long): Boolean {
            if (deleteWorks) deleted += trackId
            return deleteWorks
        }

        override suspend fun markDeleted(trackId: Long) {
            marked += trackId
        }

        override suspend fun purgeExpired(nowMs: Long) = Unit
    }

    private class FakeWaveforms : WaveformRepository {
        val forgotten = mutableListOf<Long>()

        override suspend fun peaks(track: Track): WaveformPeaks? = null

        override suspend fun forget(track: Track) {
            forgotten += track.id
        }
    }

    /** Records the removed queue positions. */
    private class QueuePlayer : PlayerController {
        override val state = MutableStateFlow(PlayerState())
        val removed = mutableListOf<Int>()

        override fun removeQueueItem(index: Int) {
            removed += index
        }

        override fun connect() = Unit

        override fun play(
            tracks: List<Track>,
            startIndex: Int,
            playlistId: Long?,
        ) = Unit

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

        override fun toggleShuffle() = Unit

        override fun cycleRepeatMode() = Unit
    }
}
