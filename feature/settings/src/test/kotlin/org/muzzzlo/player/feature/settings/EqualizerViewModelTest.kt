package org.muzzzlo.player.feature.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.model.AppLanguage
import org.muzzzlo.player.core.model.AppSettings
import org.muzzzlo.player.core.model.CrossfadeSettings
import org.muzzzlo.player.core.model.EqualizerPreset
import org.muzzzlo.player.core.model.EqualizerSettings
import org.muzzzlo.player.core.model.InterfaceTheme
import org.muzzzlo.player.core.model.PlaybackBehaviorSettings
import org.muzzzlo.player.core.model.ReplayGainSettings
import org.muzzzlo.player.core.model.SettingsRepository
import org.muzzzlo.player.core.model.StatsSettings

@OptIn(ExperimentalCoroutinesApi::class)
class EqualizerViewModelTest {
    private val settings = FakeSettings()
    private val viewModel by lazy { EqualizerViewModel(settings) }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `shows the saved settings and the clip protection of ReplayGain`() {
        val saved = EqualizerSettings(enabled = true, preset = EqualizerPreset.ROCK)
        settings.state.value = AppSettings(equalizer = saved, replayGain = ReplayGainSettings(clipProtection = false))

        assertTrue(viewModel.state.value.isLoaded)
        assertEquals(saved, viewModel.state.value.settings)
        assertFalse(viewModel.state.value.clipProtection)
        assertEquals(emptyList<EqualizerSettings>(), settings.saved)
    }

    @Test
    fun `switch and preset are shown and saved`() {
        viewModel.onEnabledChanged(true)
        viewModel.onPresetChosen(EqualizerPreset.JAZZ)

        val expected = EqualizerSettings(enabled = true, preset = EqualizerPreset.JAZZ)
        assertEquals(expected, viewModel.state.value.settings)
        assertEquals(expected, settings.saved.last())
    }

    @Test
    fun `dragging a band of a preset makes a custom curve in steps`() {
        viewModel.onPresetChosen(EqualizerPreset.BASS)

        viewModel.onBandChanged(9, 3.3f)

        val shown = viewModel.state.value.settings
        assertEquals(EqualizerPreset.CUSTOM, shown.preset)
        assertEquals(EqualizerPreset.BASS.gainsDb!!.dropLast(1) + 3.5f, shown.customDb)
    }

    @Test
    fun `the same value twice is saved once`() {
        viewModel.onBandChanged(0, 2.1f)
        viewModel.onBandChanged(0, 1.9f)

        assertEquals(1, settings.saved.size)
    }

    @Test
    fun `preamp steps stop at the ends`() {
        repeat(30) { viewModel.onPreampStepped(1) }
        assertEquals(12f, viewModel.state.value.settings.preampDb)

        repeat(60) { viewModel.onPreampStepped(-1) }
        assertEquals(-12f, viewModel.state.value.settings.preampDb)
    }

    @Test
    fun `reset goes flat and keeps the custom curve`() {
        viewModel.onBandChanged(4, 6f)
        viewModel.onPreampStepped(-4)

        viewModel.onReset()

        val shown = viewModel.state.value.settings
        assertEquals(EqualizerPreset.FLAT, shown.preset)
        assertEquals(0f, shown.preampDb)
        assertEquals(6f, shown.customDb[4])
    }

    @Test
    fun `clip protection follows a later change of ReplayGain`() {
        viewModel.state.value

        settings.state.value = settings.state.value.copy(replayGain = ReplayGainSettings(clipProtection = false))

        assertFalse(viewModel.state.value.clipProtection)
    }

    private class FakeSettings : SettingsRepository {
        val state = MutableStateFlow(AppSettings())
        val saved = mutableListOf<EqualizerSettings>()
        override val settings = state

        override suspend fun setEqualizer(settings: EqualizerSettings) {
            saved += settings
            state.value = state.value.copy(equalizer = settings.clamped())
        }

        override suspend fun setFolderIncluded(
            relativePath: String,
            included: Boolean,
        ) = Unit

        override suspend fun setMinTrackSeconds(seconds: Int) = Unit

        override suspend fun setId3v2VersionForNewTags(version: Int) = Unit

        override suspend fun setConfirmBeforeWrite(enabled: Boolean) = Unit

        override suspend fun setLanguage(language: AppLanguage) = Unit

        override suspend fun setDynamicAccent(enabled: Boolean) = Unit

        override suspend fun setGapless(enabled: Boolean) = Unit

        override suspend fun setReplayGain(settings: ReplayGainSettings) = Unit

        override suspend fun setCrossfade(settings: CrossfadeSettings) = Unit

        override suspend fun setPlaybackBehavior(settings: PlaybackBehaviorSettings) = Unit

        override suspend fun setStats(settings: StatsSettings) = Unit

        override suspend fun setTheme(theme: InterfaceTheme) = Unit

        override suspend fun setWaveform(enabled: Boolean) = Unit
    }
}
