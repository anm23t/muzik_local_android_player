package org.muzzzlo.player.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.model.AppLanguage
import org.muzzzlo.player.core.model.AppSettings
import org.muzzzlo.player.core.model.CrossfadeSettings
import org.muzzzlo.player.core.model.LibraryMaintenance
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.PlaybackBehaviorSettings
import org.muzzzlo.player.core.model.ReplayGainMode
import org.muzzzlo.player.core.model.ReplayGainSettings
import org.muzzzlo.player.core.model.SettingsRepository
import org.muzzzlo.player.core.model.TransientFocusAction
import org.muzzzlo.player.core.model.UpdateCheckStore
import javax.inject.Inject

/** A choice dialog over the settings screen. */
enum class SettingsDialog {
    MinLength,
    Id3Version,
    Language,
    ReplayGainMode,
    Preamp,
    UntaggedGain,
    Crossfade,
    TransientFocus,
    ListenThreshold,
}

/**
 * State of the settings screen.
 *
 * @property quarantineCount files waiting in quarantine; shown on the row that opens their list.
 * @property checkUpdates the user agreed to the daily check for a new release (task 1.1.9).
 */
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isScanning: Boolean = false,
    val quarantineCount: Int = 0,
    val checkUpdates: Boolean = false,
    val dialog: SettingsDialog? = null,
)

/** Minimum track lengths offered in the dialog, in seconds; 0 is "off". */
val MinLengthOptions = listOf(0, 15, 30, 45, 60, 120, 180, 300)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repository: SettingsRepository,
        private val library: LibraryRepository,
        private val maintenance: LibraryMaintenance,
        private val updates: UpdateCheckStore,
    ) : ViewModel() {
        private val local = MutableStateFlow(SettingsUiState())
        val state: StateFlow<SettingsUiState> = local.asStateFlow()

        /** Changes of the statistics section. */
        val stats =
            StatsSettingsEditor(
                current = { local.value.settings.stats },
                show = { stats -> local.update { it.copy(settings = it.settings.copy(stats = stats)) } },
                save = { stats -> viewModelScope.launch { repository.setStats(stats) } },
            )

        init {
            viewModelScope.launch {
                repository.settings.collect { settings -> local.update { it.copy(settings = settings) } }
            }
            viewModelScope.launch {
                updates.state.collect { state -> local.update { it.copy(checkUpdates = state.enabled == true) } }
            }
            refreshQuarantine()
        }

        fun onCheckUpdatesChanged(enabled: Boolean) {
            viewModelScope.launch { updates.setEnabled(enabled) }
        }

        fun onDialogRequested(dialog: SettingsDialog) = local.update { it.copy(dialog = dialog) }

        fun onDialogDismissed() = local.update { it.copy(dialog = null) }

        fun onMinLengthChosen(seconds: Int) {
            local.update { it.copy(dialog = null) }
            viewModelScope.launch {
                repository.setMinTrackSeconds(seconds)
                scan()
            }
        }

        fun onId3VersionChosen(version: Int) {
            local.update { it.copy(dialog = null) }
            viewModelScope.launch { repository.setId3v2VersionForNewTags(version) }
        }

        fun onLanguageChosen(language: AppLanguage) {
            local.update { it.copy(dialog = null) }
            viewModelScope.launch { repository.setLanguage(language) }
        }

        fun onConfirmBeforeWriteChanged(enabled: Boolean) {
            viewModelScope.launch { repository.setConfirmBeforeWrite(enabled) }
        }

        fun onDynamicAccentChanged(enabled: Boolean) {
            viewModelScope.launch { repository.setDynamicAccent(enabled) }
        }

        fun onGaplessChanged(enabled: Boolean) {
            viewModelScope.launch { repository.setGapless(enabled) }
        }

        fun onReplayGainModeChosen(mode: ReplayGainMode) {
            local.update { it.copy(dialog = null) }
            changeReplayGain { it.copy(mode = mode) }
        }

        /** Moves the preamp by [steps] of [ReplayGainSettings.STEP_DB]; the dialog stays open. */
        fun onPreampStepped(steps: Int) = changeReplayGain { it.copy(preampDb = it.preampDb + steps * STEP) }

        /** Moves the gain for untagged files by [steps] of [ReplayGainSettings.STEP_DB]; the dialog stays open. */
        fun onUntaggedGainStepped(steps: Int) =
            changeReplayGain { it.copy(untaggedGainDb = it.untaggedGainDb + steps * STEP) }

        fun onClipProtectionChanged(enabled: Boolean) = changeReplayGain { it.copy(clipProtection = enabled) }

        /**
         * Applies [change] to the shown settings at once, so quick taps on a stepper build on each other, and saves
         * the result.
         */
        private fun changeReplayGain(change: (ReplayGainSettings) -> ReplayGainSettings) {
            val updated = change(local.value.settings.replayGain).clamped()
            local.update { it.copy(settings = it.settings.copy(replayGain = updated)) }
            viewModelScope.launch { repository.setReplayGain(updated) }
        }

        /** Moves the crossfade length by [steps] seconds; the dialog stays open. */
        fun onCrossfadeStepped(steps: Int) = changeCrossfade { it.copy(seconds = it.seconds + steps) }

        fun onCrossfadeWithinAlbumChanged(enabled: Boolean) = changeCrossfade { it.copy(withinAlbum = enabled) }

        /** Like [changeReplayGain]: quick taps on the stepper build on the shown value. */
        private fun changeCrossfade(change: (CrossfadeSettings) -> CrossfadeSettings) {
            val updated = change(local.value.settings.crossfade).clamped()
            local.update { it.copy(settings = it.settings.copy(crossfade = updated)) }
            viewModelScope.launch { repository.setCrossfade(updated) }
        }

        fun onTransientFocusChosen(action: TransientFocusAction) {
            local.update { it.copy(dialog = null) }
            changePlaybackBehavior { it.copy(transientFocus = action) }
        }

        fun onResumeOnHeadsetChanged(enabled: Boolean) = changePlaybackBehavior { it.copy(resumeOnHeadset = enabled) }

        fun onSkipSilenceChanged(enabled: Boolean) = changePlaybackBehavior { it.copy(skipSilence = enabled) }

        private fun changePlaybackBehavior(change: (PlaybackBehaviorSettings) -> PlaybackBehaviorSettings) {
            val updated = change(local.value.settings.playbackBehavior)
            local.update { it.copy(settings = it.settings.copy(playbackBehavior = updated)) }
            viewModelScope.launch { repository.setPlaybackBehavior(updated) }
        }

        fun onRescanClicked() {
            if (local.value.isScanning) return
            viewModelScope.launch { scan() }
        }

        /** The scan is not cancelled when the user leaves the screen: a half-done scan helps nobody. */
        private suspend fun scan() {
            local.update { it.copy(isScanning = true) }
            try {
                withContext(NonCancellable) { library.scan() }
            } finally {
                local.update { it.copy(isScanning = false) }
            }
            refreshQuarantine()
        }

        private companion object {
            const val STEP = ReplayGainSettings.STEP_DB
        }

        private fun refreshQuarantine() {
            viewModelScope.launch {
                val count = maintenance.quarantined().size
                local.update { it.copy(quarantineCount = count) }
            }
        }
    }
