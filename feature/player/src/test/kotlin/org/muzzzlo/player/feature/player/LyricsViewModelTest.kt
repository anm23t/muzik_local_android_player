package org.muzzzlo.player.feature.player

import kotlinx.coroutines.CompletableDeferred
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.model.Lyrics
import org.muzzzlo.player.core.model.LyricsRepository
import org.muzzzlo.player.core.model.LyricsSource
import org.muzzzlo.player.core.model.PlayerState
import org.muzzzlo.player.core.model.Track
import org.muzzzlo.player.core.model.TrackLyrics

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsViewModelTest {
    private val withLyrics = Track(1, "content://1", "A", listOf("X"), null, null, 1_000, null)
    private val other = Track(2, "content://2", "B", listOf("Y"), null, null, 1_000, null)
    private val without = Track(3, "content://3", "C", listOf("Z"), null, null, 1_000, null)
    private val player = FakePlayerController()
    private val settings = FakeSettings()
    private val repository = FakeLyrics()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): LyricsViewModel =
        LyricsViewModel(player, settings, repository).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        }

    private fun play(track: Track?) {
        player.state.value = PlayerState(currentTrack = track)
    }

    @Test
    fun `the button turns the lyrics on and off and the mode is saved`() =
        runTest {
            val vm = viewModel()
            play(withLyrics)
            assertTrue(vm.state.value.available)
            assertFalse(vm.state.value.showLyrics)

            vm.onToggle()
            assertTrue(settings.settings.value.lyricsShown)
            assertTrue(vm.state.value.showLyrics)

            vm.onToggle()
            assertFalse(settings.settings.value.lyricsShown)
            assertFalse(vm.state.value.showLyrics)
        }

    @Test
    fun `a track without lyrics shows the cover and its button does nothing`() =
        runTest {
            val vm = viewModel()
            play(without)

            vm.onToggle()
            assertFalse(vm.state.value.available)
            assertFalse(settings.settings.value.lyricsShown)
            assertFalse(vm.onSwipeUp())
        }

    @Test
    fun `the mode stays over a track without lyrics and comes back with the next one`() =
        runTest {
            settings.setLyricsShown(true)
            val vm = viewModel()
            play(withLyrics)
            assertTrue(vm.state.value.showLyrics)

            play(without)
            assertFalse(vm.state.value.showLyrics)
            assertTrue(settings.settings.value.lyricsShown)

            play(other)
            assertTrue(vm.state.value.showLyrics)
        }

    @Test
    fun `the lyrics stay on screen while the next track loads`() =
        runTest {
            settings.setLyricsShown(true)
            val vm = viewModel()
            play(withLyrics)
            val pending = CompletableDeferred<TrackLyrics?>()
            repository.next = pending

            play(other)
            assertTrue(vm.state.value.isLoading)
            assertTrue(vm.state.value.showLyrics)

            pending.complete(null)
            assertFalse(vm.state.value.showLyrics)
        }

    @Test
    fun `a swipe up opens the lyrics`() =
        runTest {
            val vm = viewModel()
            play(withLyrics)

            assertTrue(vm.onSwipeUp())
            assertTrue(vm.state.value.showLyrics)
        }

    @Test
    fun `a tapped line seeks to its time`() =
        runTest {
            val vm = viewModel()
            vm.onLineTapped(12_500)
            assertEquals(listOf(12_500L), player.seeks)
        }

    @Test
    fun `nothing playing has no lyrics`() =
        runTest {
            settings.setLyricsShown(true)
            val vm = viewModel()
            play(null)
            assertFalse(vm.state.value.showLyrics)
            assertFalse(vm.state.value.available)
        }

    @Test
    fun `showing follows the wish, the lyrics and, while loading, the screen before`() {
        assertTrue(showsLyrics(wanted = true, loaded = true, hasLyrics = true, wasShowing = false))
        assertFalse(showsLyrics(wanted = true, loaded = true, hasLyrics = false, wasShowing = true))
        assertTrue(showsLyrics(wanted = true, loaded = false, hasLyrics = false, wasShowing = true))
        assertFalse(showsLyrics(wanted = true, loaded = false, hasLyrics = false, wasShowing = false))
        assertFalse(showsLyrics(wanted = false, loaded = true, hasLyrics = true, wasShowing = true))
    }

    /** Track 3 has no lyrics, the others plain ones; [next] holds back the next answer. */
    private class FakeLyrics : LyricsRepository {
        var next: CompletableDeferred<TrackLyrics?>? = null

        override suspend fun lyricsOf(trackId: Long): TrackLyrics? {
            next?.let {
                next = null
                return it.await()
            }
            return if (trackId == 3L) null else TrackLyrics(Lyrics.Plain("Text $trackId"), LyricsSource.TAG)
        }
    }
}
