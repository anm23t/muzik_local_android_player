package org.muzzzlo.player.feature.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.model.PlayerState
import org.muzzzlo.player.core.model.SleepTimer
import org.muzzzlo.player.core.model.SleepTimerMode
import org.muzzzlo.player.core.model.SleepTimerRules

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val timer = FakeSleepTimer { scope.testScheduler.currentTime }
    private val player = FakePlayerController()

    init {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): SleepTimerViewModel =
        SleepTimerViewModel(timer, player).also { vm -> backgroundScope.launch { vm.state.collect {} } }

    @Test
    fun `no timer, no badge`() =
        scope.runTest {
            val vm = viewModel()
            runCurrent()

            assertNull(vm.state.value)
        }

    @Test
    fun `minutes count down every second on virtual time`() =
        scope.runTest {
            val vm = viewModel()
            vm.onMinutesChosen(15)
            runCurrent()
            assertEquals(15 * 60_000L, vm.state.value?.remainingMs)

            advanceTimeBy(61_000)
            runCurrent()

            assertEquals(15 * 60_000L - 61_000, vm.state.value?.remainingMs)
        }

    @Test
    fun `the countdown stops at zero`() =
        scope.runTest {
            val vm = viewModel()
            vm.onMinutesChosen(1)

            advanceTimeBy(5 * 60_000)
            runCurrent()

            assertEquals(0L, vm.state.value?.remainingMs)
        }

    @Test
    fun `end of track follows the position of the player`() =
        scope.runTest {
            val vm = viewModel()
            player.state.value = PlayerState(positionMs = 30_000, durationMs = 200_000)
            vm.onEndOfTrackChosen()
            runCurrent()
            assertEquals(SleepTimerUi(SleepTimerMode.EndOfTrack, 170_000), vm.state.value)

            player.state.value = PlayerState(positionMs = 190_000, durationMs = 200_000)
            runCurrent()

            assertEquals(10_000L, vm.state.value?.remainingMs)
        }

    @Test
    fun `end of track without a length shows no time`() =
        scope.runTest {
            val vm = viewModel()
            vm.onEndOfTrackChosen()
            runCurrent()

            assertEquals(SleepTimerUi(SleepTimerMode.EndOfTrack, null), vm.state.value)
        }

    @Test
    fun `cancel removes the badge, also when the service clears the timer`() =
        scope.runTest {
            val vm = viewModel()
            vm.onMinutesChosen(30)
            runCurrent()

            vm.onCancel()
            runCurrent()
            assertNull(vm.state.value)

            vm.onEndOfTrackChosen()
            runCurrent()
            timer.mode.value = null
            runCurrent()
            assertNull(vm.state.value)
        }

    private class FakeSleepTimer(
        private val clock: () -> Long,
    ) : SleepTimer {
        override val mode = MutableStateFlow<SleepTimerMode?>(null)

        override fun nowMs(): Long = clock()

        override fun start(minutes: Int) {
            mode.value = SleepTimerRules.after(minutes, clock())
        }

        override fun startEndOfTrack() {
            mode.value = SleepTimerMode.EndOfTrack
        }

        override fun cancel() {
            mode.value = null
        }
    }
}
