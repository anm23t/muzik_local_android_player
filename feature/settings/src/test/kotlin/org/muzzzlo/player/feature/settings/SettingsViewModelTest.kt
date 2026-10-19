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
import org.muzzzlo.player.core.model.LibraryFolder
import org.muzzzlo.player.core.model.LibraryMaintenance
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.QuarantinedFile
import org.muzzzlo.player.core.model.ReplayGainMode
import org.muzzzlo.player.core.model.ReplayGainSettings
import org.muzzzlo.player.core.model.SettingsRepository
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val settings = FakeSettings()
    private val viewModel by lazy { SettingsViewModel(settings, unusedLibrary(), NoMaintenance) }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `choosing a mode saves it and closes the dialog`() {
        viewModel.onDialogRequested(SettingsDialog.ReplayGainMode)
        viewModel.onReplayGainModeChosen(ReplayGainMode.ALBUM)

        assertEquals(ReplayGainMode.ALBUM, settings.state.value.replayGain.mode)
        assertNull(viewModel.state.value.dialog)
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

    /**
     * Settings in memory. [saved] lists every ReplayGain save; with [saveOnly] off the flow does not change, as when
     * DataStore has not emitted yet, so the view model must build on its own state.
     */
    private class FakeSettings : SettingsRepository {
        val state = MutableStateFlow(AppSettings())
        val saved = mutableListOf<ReplayGainSettings>()
        var saveOnly = true
        override val settings = state

        override suspend fun setReplayGain(settings: ReplayGainSettings) {
            saved += settings
            if (saveOnly) state.value = state.value.copy(replayGain = settings.clamped())
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
