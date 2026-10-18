package org.muzzzlo.player.feature.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.muzzzlo.player.core.designsystem.component.TrackAction
import org.muzzzlo.player.core.model.Album
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.Track

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val track = Track(1, "content://1", "Title", listOf("A"), null, null, 1_000, null)

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @ParameterizedTest
    @EnumSource(TrackAction::class)
    fun `only queue actions reach the player`(action: TrackAction) {
        val player = FakePlayerController()
        LibraryViewModel(EmptyLibrary, player).onTrackAction(track, action)

        val expectedNext = if (action == TrackAction.PlayNext) listOf(track) else emptyList()
        val expectedQueue = if (action == TrackAction.AddToQueue) listOf(track) else emptyList()
        assertEquals(expectedNext, player.playedNext)
        assertEquals(expectedQueue, player.addedToQueue)
    }

    private object EmptyLibrary : LibraryRepository {
        override fun observeTracks(): Flow<List<Track>> = flowOf(emptyList())

        override fun observeAlbums(): Flow<List<Album>> = flowOf(emptyList())

        override fun observeAlbumTracks(albumId: Long): Flow<List<Track>> = flowOf(emptyList())

        override suspend fun tracksByIds(ids: List<Long>): List<Track> = emptyList()

        override suspend fun scan() = Unit
    }
}
