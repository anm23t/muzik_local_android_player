package org.muzzzlo.player.feature.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.model.AppLanguage
import org.muzzzlo.player.core.model.AppSettings
import org.muzzzlo.player.core.model.CrossfadeSettings
import org.muzzzlo.player.core.model.InterfaceTheme
import org.muzzzlo.player.core.model.LibraryFolder
import org.muzzzlo.player.core.model.LibraryMaintenance
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.PlaybackBehaviorSettings
import org.muzzzlo.player.core.model.QuarantinedFile
import org.muzzzlo.player.core.model.ReplayGainMode
import org.muzzzlo.player.core.model.ReplayGainSettings
import org.muzzzlo.player.core.model.SettingsRepository
import org.muzzzlo.player.core.model.StatsSettings
import org.muzzzlo.player.core.model.TransientFocusAction
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val settings = FakeSettings()
    private val updates = FakeUpdateStore()
    private val viewModel by lazy { SettingsViewModel(settings, unusedLibrary(), NoMaintenance, updates) }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the update switch follows the saved answer and saves a change`() {
        assertEquals(false, viewModel.state.value.checkUpdates)

        viewModel.onCheckUpdatesChanged(true)
        assertEquals(true, updates.current.value.enabled)
        assertEquals(true, viewModel.state.value.checkUpdates)

        viewModel.onCheckUpdatesChanged(false)
        assertEquals(false, updates.current.value.enabled)
        assertEquals(false, viewModel.state.value.checkUpdates)
    }

    @Test
    fun `choosing a mode saves it and closes the dialog`() {
        viewModel.onDialogRequested(SettingsDialog.ReplayGainMode)
        viewModel.onReplayGainModeChosen(ReplayGainMode.ALBUM)

        assertEquals(ReplayGainMode.ALBUM, settings.state.value.replayGain.mode)
        assertNull(viewModel.state.value.dialog)
    }

    @Test
    fun `choosing a theme saves it and closes the dialog`() {
        viewModel.onDialogRequested(SettingsDialog.Theme)
        viewModel.ui.onThemeChosen(InterfaceTheme.HUGE)

        assertEquals(InterfaceTheme.HUGE, settings.state.value.theme)
        assertNull(viewModel.state.value.dialog)
    }

    @Test
    fun `the waveform switch saves the setting`() {
        viewModel.ui.onWaveformChanged(false)

        assertEquals(false, settings.state.value.waveform)
    }

    @Test
    fun `quick steps build on each other and the dialog stays open`() {
        settings.saveOnly = false
        viewModel.onDialogRequested(SettingsDialog.Preamp)
        repeat(3) { viewModel.onPreampStepped(1) }
        viewModel.onPreampStepped(-1)

        assertEquals(1f, viewModel.state.value.settings.replayGain.preampDb)
        assertEquals(1f, settings.saved.last().preampDb)
        assertEquals(SettingsDialog.Preamp, viewModel.state.value.dialog)
    }

    @Test
    fun `steps stop at the ends of the range`() {
        repeat(40) { viewModel.onPreampStepped(1) }
        repeat(40) { viewModel.onUntaggedGainStepped(1) }

        assertEquals(ReplayGainSettings.PREAMP_MAX_DB, settings.state.value.replayGain.preampDb)
        assertEquals(0f, settings.state.value.replayGain.untaggedGainDb)

        repeat(80) { viewModel.onUntaggedGainStepped(-1) }
        assertEquals(ReplayGainSettings.UNTAGGED_MIN_DB, settings.state.value.replayGain.untaggedGainDb)
    }

    @Test
    fun `clip protection is switched without touching the rest`() {
        viewModel.onUntaggedGainStepped(-2)
        viewModel.onClipProtectionChanged(false)

        assertEquals(
            ReplayGainSettings(untaggedGainDb = -7f, clipProtection = false),
            settings.state.value.replayGain,
        )
    }

    @Test
    fun `crossfade steps build on each other, stop at the ends and keep the dialog open`() {
        settings.saveOnly = false
        viewModel.onDialogRequested(SettingsDialog.Crossfade)
        repeat(5) { viewModel.onCrossfadeStepped(1) }
        viewModel.onCrossfadeStepped(-1)

        assertEquals(4, viewModel.state.value.settings.crossfade.seconds)
        assertEquals(CrossfadeSettings(seconds = 4), settings.savedCrossfade.last())
        assertEquals(SettingsDialog.Crossfade, viewModel.state.value.dialog)

        repeat(20) { viewModel.onCrossfadeStepped(1) }
        assertEquals(CrossfadeSettings.MAX_SECONDS, viewModel.state.value.settings.crossfade.seconds)
        repeat(20) { viewModel.onCrossfadeStepped(-1) }
        assertEquals(0, viewModel.state.value.settings.crossfade.seconds)
    }

    @Test
    fun `crossfade within album is switched without touching the length`() {
        viewModel.onCrossfadeStepped(3)
        viewModel.onCrossfadeWithinAlbumChanged(true)

        assertEquals(CrossfadeSettings(seconds = 3, withinAlbum = true), settings.state.value.crossfade)
    }

    @Test
    fun `playback behaviour changes build on each other and the focus choice closes its dialog`() {
        viewModel.onDialogRequested(SettingsDialog.TransientFocus)
        viewModel.onTransientFocusChosen(TransientFocusAction.PAUSE)
        viewModel.onResumeOnHeadsetChanged(true)
        viewModel.onSkipSilenceChanged(true)

        val expected = PlaybackBehaviorSettings(TransientFocusAction.PAUSE, resumeOnHeadset = true, skipSilence = true)
        assertEquals(expected, settings.state.value.playbackBehavior)
        assertEquals(expected, viewModel.state.value.settings.playbackBehavior)
        assertNull(viewModel.state.value.dialog)
    }

    @Test
    fun `threshold steps build on each other, stop at the ends and keep the dialog open`() {
        settings.saveOnly = false
        viewModel.onDialogRequested(SettingsDialog.ListenThreshold)
        repeat(2) { viewModel.stats.onPercentStepped(1) }
        viewModel.stats.onMinutesStepped(-1)

        assertEquals(StatsSettings(thresholdPercent = 70, thresholdMinutes = 3), viewModel.state.value.settings.stats)
        assertEquals(StatsSettings(thresholdPercent = 70, thresholdMinutes = 3), settings.savedStats.last())
        assertEquals(SettingsDialog.ListenThreshold, viewModel.state.value.dialog)

        repeat(20) { viewModel.stats.onPercentStepped(1) }
        repeat(20) { viewModel.stats.onMinutesStepped(-1) }
        assertEquals(StatsSettings(thresholdPercent = 90, thresholdMinutes = 1), viewModel.state.value.settings.stats)
    }

    @Test
    fun `private mode is switched without touching the threshold`() {
        viewModel.stats.onMinutesStepped(2)
        viewModel.stats.onPrivateModeChanged(true)

        assertEquals(StatsSettings(thresholdMinutes = 6, privateMode = true), settings.state.value.stats)
    }

    /**
     * Settings in memory. [saved] lists every ReplayGain save; with [saveOnly] off the flow does not change, as when
     * DataStore has not emitted yet, so the view model must build on its own state.
     */
    private class FakeSettings : SettingsRepository {
        val state = MutableStateFlow(AppSettings())
        val saved = mutableListOf<ReplayGainSettings>()
        val savedCrossfade = mutableListOf<CrossfadeSettings>()
        val savedStats = mutableListOf<StatsSettings>()
        var saveOnly = true
        override val settings = state

        override suspend fun setReplayGain(settings: ReplayGainSettings) {
            saved += settings
            if (saveOnly) state.value = state.value.copy(replayGain = settings.clamped())
        }

        override suspend fun setCrossfade(settings: CrossfadeSettings) {
            savedCrossfade += settings
            if (saveOnly) state.value = state.value.copy(crossfade = settings.clamped())
        }

        override suspend fun setPlaybackBehavior(settings: PlaybackBehaviorSettings) {
            state.value = state.value.copy(playbackBehavior = settings)
        }

        override suspend fun setWaveform(enabled: Boolean) {
            state.value = state.value.copy(waveform = enabled)
        }

        override suspend fun setTheme(theme: InterfaceTheme) {
            state.value = state.value.copy(theme = theme)
        }

        override suspend fun setStats(settings: StatsSettings) {
            savedStats += settings
            if (saveOnly) state.value = state.value.copy(stats = settings.clamped())
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
    }

    private object NoMaintenance : LibraryMaintenance {
        override suspend fun folders(): List<LibraryFolder> = emptyList()

        override suspend fun quarantined(): List<QuarantinedFile> = emptyList()

        override suspend fun retryQuarantined(uri: String) = Unit
    }

    /** The ReplayGain rows never touch the library; any call fails the test. */
    private fun unusedLibrary(): LibraryRepository =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(LibraryRepository::class.java)) { _, method, _ ->
            error("unexpected call to ${method.name}")
        } as LibraryRepository
}
