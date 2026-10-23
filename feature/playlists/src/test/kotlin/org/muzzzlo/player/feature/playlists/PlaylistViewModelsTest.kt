package org.muzzzlo.player.feature.playlists

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.designsystem.component.PlaylistChoice
import org.muzzzlo.player.core.designsystem.component.TrackAction
import org.muzzzlo.player.core.model.AutoPlaylist
import org.muzzzlo.player.core.model.PlayerState
import org.muzzzlo.player.core.model.Track

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistViewModelsTest {
    private val a = Track(1, "content://1", "A", listOf("X"), null, null, 1_000, null)
    private val b = Track(2, "content://2", "B", listOf("Y"), null, null, 1_000, null)
    private val c = Track(3, "content://3", "C", emptyList(), null, null, 1_000, null)
    private val playlists = FakePlaylists(listOf(a, b, c).associateBy { it.id })
    private val autoPlaylists = FakeAutoPlaylists()
    private val player = FakePlayerController()
    private val transfer = FakePlaylistTransfer()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.card(playlistId: Long): PlaylistViewModel {
        val savedState = SavedStateHandle(mapOf(PLAYLIST_ID_ARG to playlistId))
        val viewModel = PlaylistViewModel(playlists, FakeLibrary(), player, FakeTrackMarks(), transfer, savedState)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        return viewModel
    }

    private suspend fun mix(vararg tracks: Track): Long =
        playlists.create("Mix").also { playlists.addTracks(it, tracks.map(Track::id)) }

    @Test
    fun `the list shows playlists and creates one with a clean name`() =
        runTest {
            val viewModel = PlaylistListViewModel(playlists, autoPlaylists, transfer)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

            viewModel.onCreate("  Road \n trip ")
            viewModel.onCreate("   ")

            assertTrue(viewModel.state.value.isLoaded)
            assertEquals(listOf("Road trip"), viewModel.state.value.playlists.map { it.name })
        }

    private fun TestScope.autoCard(key: String): AutoPlaylistViewModel {
        val savedState = SavedStateHandle(mapOf(AUTO_PLAYLIST_ARG to key))
        val viewModel =
            AutoPlaylistViewModel(autoPlaylists, playlists, FakeLibrary(), player, FakeTrackMarks(), savedState)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        return viewModel
    }

    @Test
    fun `the list puts the automatic playlists first with their size and first cover`() =
        runTest {
            autoPlaylists.tracks.value = mapOf(AutoPlaylist.Favorites to listOf(c, a))
            val viewModel = PlaylistListViewModel(playlists, autoPlaylists, transfer)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

            assertEquals(
                listOf(
                    AutoPlaylistSummary(AutoPlaylist.Favorites, 2, c.id),
                    AutoPlaylistSummary(AutoPlaylist.TopSongs, 0, null),
                ),
                viewModel.state.value.auto,
            )

            autoPlaylists.tracks.value += AutoPlaylist.TopSongs to listOf(b)
            assertEquals(1, viewModel.state.value.auto.last().trackCount)
        }

    @Test
    fun `an automatic playlist follows its source and plays without a playlist mark`() =
        runTest {
            autoPlaylists.tracks.value = mapOf(AutoPlaylist.TopSongs to listOf(b, a))
            val viewModel = autoCard("top-songs")

            assertEquals(AutoPlaylist.TopSongs, viewModel.state.value.playlist)
            assertEquals(listOf(b, a), viewModel.state.value.tracks)

            viewModel.onTrackClicked(1)
            viewModel.onPlay()
            viewModel.onTrackClicked(5)
            autoPlaylists.tracks.value = mapOf(AutoPlaylist.TopSongs to listOf(a))

            assertEquals(listOf(listOf(b, a) to 1, listOf(b, a) to 0), player.played)
            assertEquals(listOf<Long?>(null, null), player.playedFrom)
            assertEquals(listOf(a), viewModel.state.value.tracks)
        }

    @Test
    fun `an unknown key shows no playlist`() =
        runTest {
            val viewModel = autoCard("recent")

            assertNull(viewModel.state.value.playlist)
            assertTrue(viewModel.state.value.isLoaded)
            assertTrue(viewModel.state.value.tracks.isEmpty())
        }

    @Test
    fun `the menu of an automatic playlist queues the track and saves marks`() =
        runTest {
            val viewModel = autoCard("favorites")

            viewModel.onTrackAction(a, TrackAction.PlayNext)
            viewModel.onTrackAction(a, TrackAction.AddToQueue)
            viewModel.onTrackAction(a, TrackAction.RemoveFromPlaylist)
            viewModel.trackMarks(a.id).onFavorite(true)

            assertEquals(listOf(a), player.playedNext)
            assertEquals(listOf(a), player.addedToQueue)
            assertTrue(viewModel.trackMarks(a.id).load().isFavorite)
        }

    @Test
    fun `play starts the playlist from the tapped track and remembers it`() =
        runTest {
            val id = mix(a, b, c)
            val viewModel = card(id)

            viewModel.onTrackClicked(1)
            viewModel.onPlay()
            viewModel.onTrackClicked(9)

            assertEquals(listOf(listOf(a, b, c) to 1, listOf(a, b, c) to 0), player.played)
            assertEquals(listOf(id, id), player.playedFrom)
            assertEquals(listOf(id, id), playlists.played)
        }

    @Test
    fun `a moved row is saved as the new order of entries`() =
        runTest {
            val id = mix(a, b, c)
            val viewModel = card(id)
            val ids = viewModel.state.value.entries.map { it.entryId }

            viewModel.onMoved(0, 2)
            viewModel.onMoved(1, 1)

            assertEquals(listOf(listOf(ids[1], ids[2], ids[0])), playlists.reordered)
            assertEquals(listOf(b, c, a), viewModel.state.value.tracks)
        }

    @Test
    fun `remove from playlist takes out only that entry`() =
        runTest {
            val id = mix(a, b, a)
            val viewModel = card(id)
            val second = viewModel.state.value.entries[2]

            viewModel.onTrackAction(second, TrackAction.RemoveFromPlaylist)

            assertEquals(listOf(a, b), viewModel.state.value.tracks)
        }

    @Test
    fun `queue actions reach the player`() =
        runTest {
            val viewModel = card(mix(a))
            val entry = viewModel.state.value.entries.single()

            viewModel.onTrackAction(entry, TrackAction.PlayNext)
            viewModel.onTrackAction(entry, TrackAction.AddToQueue)
            viewModel.onTrackAction(entry, TrackAction.Rate)

            assertEquals(listOf(a), player.playedNext)
            assertEquals(listOf(a), player.addedToQueue)
        }

    @Test
    fun `rename keeps a clean name and ignores a blank one, delete removes the playlist`() =
        runTest {
            val id = mix(a)
            val viewModel = card(id)

            viewModel.onRename("  Late   night ")
            viewModel.onRename(" ")
            assertEquals("Late night", viewModel.state.value.playlist?.name)

            viewModel.onDelete()
            assertNull(viewModel.state.value.playlist)
            assertTrue(viewModel.state.value.isLoaded)
        }

    @Test
    fun `the current track is marked`() =
        runTest {
            val viewModel = card(mix(a, b))

            player.state.value = PlayerState(currentTrack = b)

            assertEquals(b.id, viewModel.state.value.currentTrackId)
        }

    @Test
    fun `add to playlist from the card offers every playlist and creates a new one`() =
        runTest {
            val id = mix(a)
            val viewModel = card(id)
            val target = viewModel.addToPlaylist(a.id)

            assertEquals(listOf(PlaylistChoice(id, "Mix")), target.load())
            target.onAdd(id)
            target.onCreate("Other")

            assertEquals(listOf(a, a), viewModel.state.value.tracks)
            assertEquals(listOf("Mix", "Other"), playlists.playlists.value.map { it.name })
        }

    @Test
    fun `import shows its result until dismissed`() =
        runTest {
            val viewModel = PlaylistListViewModel(playlists, autoPlaylists, transfer)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

            viewModel.onImport("content://doc/1", "Playlist")

            assertEquals(listOf("content://doc/1" to "Playlist"), transfer.imports)
            assertEquals(PlaylistFileMessage.Imported(transfer.importResult), viewModel.state.value.message)
            viewModel.onMessageDismissed()
            assertNull(viewModel.state.value.message)
        }

    @Test
    fun `unreadable import file becomes a file error`() =
        runTest {
            transfer.failing = true
            val viewModel = PlaylistListViewModel(playlists, autoPlaylists, transfer)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

            viewModel.onImport("content://doc/1", "Playlist")

            assertEquals(PlaylistFileMessage.FileError, viewModel.state.value.message)
            assertEquals(false, viewModel.state.value.busy)
        }

    @Test
    fun `export writes this playlist and reports it`() =
        runTest {
            val id = mix(a, b)
            val viewModel = card(id)

            viewModel.onExport("content://doc/out")

            assertEquals(listOf(id to "content://doc/out"), transfer.exports)
            assertEquals(PlaylistFileMessage.Exported, viewModel.state.value.message)
        }

    @Test
    fun `export without access becomes a file error`() =
        runTest {
            transfer.failing = true
            val viewModel = card(mix(a))

            viewModel.onExport("content://doc/out")

            assertEquals(PlaylistFileMessage.FileError, viewModel.state.value.message)
        }
}
