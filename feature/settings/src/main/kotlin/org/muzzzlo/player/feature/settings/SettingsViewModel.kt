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
import org.muzzzlo.player.core.model.LibraryMaintenance
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.SettingsRepository
import javax.inject.Inject

/** A choice dialog over the settings screen. */
enum class SettingsDialog { MinLength, Id3Version, Language }

/**
 * State of the settings screen.
 *
 * @property quarantineCount files waiting in quarantine; shown on the row that opens their list.
 */
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isScanning: Boolean = false,
    val quarantineCount: Int = 0,
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
    ) : ViewModel() {
        private val local = MutableStateFlow(SettingsUiState())
        val state: StateFlow<SettingsUiState> = local.asStateFlow()

        init {
            viewModelScope.launch {
                repository.settings.collect { settings -> local.update { it.copy(settings = settings) } }
            }
            refreshQuarantine()
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

        private fun refreshQuarantine() {
            viewModelScope.launch {
                val count = maintenance.quarantined().size
                local.update { it.copy(quarantineCount = count) }
            }
        }
    }
