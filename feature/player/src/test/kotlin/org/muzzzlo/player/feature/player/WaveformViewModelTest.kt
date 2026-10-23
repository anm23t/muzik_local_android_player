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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.model.PlayerState
import org.muzzzlo.player.core.model.Track
import org.muzzzlo.player.core.model.WaveformPeaks
import org.muzzzlo.player.core.model.WaveformRepository

@OptIn(ExperimentalCoroutinesApi::class)
class WaveformViewModelTest {
    private val a = Track(1, "content://1", "A", listOf("X"), null, null, 1_000, null)
    private val b = Track(2, "content://2", "B", listOf("Y"), null, null, 1_000, null)
    private val player = FakePlayerController()
    private val settings = FakeSettings()
    private val waveforms = FakeWaveforms()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): WaveformViewModel =
        WaveformViewModel(player, settings, waveforms).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.peaks.collect {} }
        }

    private fun play(track: Track?) {
        player.state.value = PlayerState(currentTrack = track)
    }

    @Test
    fun `peaks of the playing track`() =
        runTest {
            val vm = viewModel()

            play(a)

            assertEquals(waveforms.peaksOf(a), vm.peaks.value)
            assertEquals(listOf(a.uri), waveforms.asked)
        }

    @Test
    fun `nothing is decoded while the setting is off`() =
        runTest {
            settings.setWaveform(false)
            val vm = viewModel()

            play(a)

            assertNull(vm.peaks.value)
            assertEquals(emptyList<String>(), waveforms.asked)
        }

    @Test
    fun `turning the setting off hides the waveform`() =
        runTest {
            val vm = viewModel()
            play(a)

            settings.setWaveform(false)

            assertNull(vm.peaks.value)
        }

    @Test
    fun `a new track starts from the plain bar until its peaks are ready`() =
        runTest {
            val vm = viewModel()
            play(a)
            val pending = CompletableDeferred<WaveformPeaks?>()
            waveforms.next = pending

            play(b)
            assertNull(vm.peaks.value)

            pending.complete(waveforms.peaksOf(b))
            assertEquals(waveforms.peaksOf(b), vm.peaks.value)
        }

    @Test
    fun `changed metadata of the same file keeps the waveform`() =
        runTest {
            val vm = viewModel()
            play(a)

            play(a.copy(ratingTenths = 80))

            assertEquals(waveforms.peaksOf(a), vm.peaks.value)
            assertEquals(1, waveforms.asked.size)
        }

    @Test
    fun `a file that cannot be decoded keeps the plain bar`() =
        runTest {
            waveforms.next = CompletableDeferred<WaveformPeaks?>(null)
            val vm = viewModel()

            play(a)

            assertNull(vm.peaks.value)
        }

    private class FakeWaveforms : WaveformRepository {
        val asked = mutableListOf<String>()
        var next: CompletableDeferred<WaveformPeaks?>? = null

        fun peaksOf(track: Track) = WaveformPeaks(floatArrayOf(track.id / 10f))

        override suspend fun peaks(track: Track): WaveformPeaks? {
            asked += track.uri
            val pending = next ?: return peaksOf(track)
            next = null
            return pending.await()
        }
    }
}
