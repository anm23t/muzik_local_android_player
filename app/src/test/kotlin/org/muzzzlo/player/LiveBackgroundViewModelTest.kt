package org.muzzzlo.player

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.model.AppSettings
import org.muzzzlo.player.core.model.BackgroundPaletteRepository
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.PlayerState
import org.muzzzlo.player.core.model.SettingsRepository
import org.muzzzlo.player.core.model.Track
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class LiveBackgroundViewModelTest {
    private val playerState = MutableStateFlow(PlayerState())
    private val settingsState = MutableStateFlow(AppSettings())
    private val palettes =
        object : BackgroundPaletteRepository {
            override suspend fun paletteOf(trackId: Long) = List(4) { (0xFF000000 + trackId * 16 + it).toInt() }

            override val defaultPalette = List(4) { 0xFFCD253E.toInt() }
        }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel() =
        LiveBackgroundViewModel(player(), settings(), palettes).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        }

    private fun track(id: Long) = Track(id, "u$id", "T", emptyList(), null, null, 1, null)

    @Test
    fun `an empty queue has no palette`() =
        runTest {
            assertNull(viewModel().state.value.palette)
        }

    @Test
    fun `the palette follows the cover of the current track, also on pause`() =
        runTest {
            val vm = viewModel()
            playerState.value = PlayerState(currentTrack = track(2), isPlaying = false)
            assertEquals(palettes.paletteOf(2).map(::Color), vm.state.value.palette)

            playerState.value = PlayerState(currentTrack = track(3), isPlaying = true)
            assertEquals(palettes.paletteOf(3).map(::Color), vm.state.value.palette)
        }

    @Test
    fun `without the dynamic accent the palette comes from the red`() =
        runTest {
            settingsState.value = AppSettings(dynamicAccent = false)
            val vm = viewModel()
            playerState.value = PlayerState(currentTrack = track(2))
            assertEquals(palettes.defaultPalette.map(::Color), vm.state.value.palette)
        }

    @Test
    fun `the setting is passed on`() =
        runTest {
            settingsState.value = AppSettings(animatedBackground = false)
            assertFalse(viewModel().state.value.enabled)
        }

    @Test
    fun `colored only with the setting, a track and a screen that allows it`() {
        val playing = LiveBackgroundUiState(enabled = true, palette = listOf(Color.Red))
        assertTrue(backgroundColored(playing, "home"))
        assertTrue(backgroundColored(playing, "album/{albumId}"))
        assertTrue(backgroundColored(playing, null))
        listOf("settings", "settingsFolders", "settingsEqualizer", "settingsQuarantine", "stats").forEach {
            assertFalse(backgroundColored(playing, it), it)
        }
        assertFalse(backgroundColored(playing.copy(palette = null), "home"))
        assertFalse(backgroundColored(playing.copy(enabled = false), "home"))
    }

    private fun player(): PlayerController =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(PlayerController::class.java)) { _, method, _ ->
            check(method.name == "getState") { "unexpected call to ${method.name}" }
            playerState
        } as PlayerController

    private fun settings(): SettingsRepository =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SettingsRepository::class.java)) { _, method, _ ->
            check(method.name == "getSettings") { "unexpected call to ${method.name}" }
            settingsState
        } as SettingsRepository
}
