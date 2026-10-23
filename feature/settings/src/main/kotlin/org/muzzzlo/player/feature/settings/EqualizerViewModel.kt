package org.muzzzlo.player.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.muzzzlo.player.core.model.Equalizer
import org.muzzzlo.player.core.model.EqualizerPreset
import org.muzzzlo.player.core.model.EqualizerSettings
import org.muzzzlo.player.core.model.SettingsRepository
import javax.inject.Inject

/**
 * What the equalizer screen renders.
 *
 * @property clipProtection the ReplayGain clip protection, which also lowers the equalizer output.
 */
data class EqualizerUiState(
    val settings: EqualizerSettings = EqualizerSettings(),
    val clipProtection: Boolean = true,
    val isLoaded: Boolean = false,
)

/**
 * The equalizer screen (task 1.2.8). A change is shown at once and saved after; a slider drag sends many changes,
 * the saving keeps only the latest one while a write is running (a [StateFlow] is conflated), so the sound follows
 * the finger without queueing up writes.
 */
@HiltViewModel
class EqualizerViewModel
    @Inject
    constructor(
        private val repository: SettingsRepository,
    ) : ViewModel() {
        private val local = MutableStateFlow(EqualizerUiState())
        val state: StateFlow<EqualizerUiState> = local.asStateFlow()

        /** The settings waiting to be written; null until the user changes something. */
        private val pending = MutableStateFlow<EqualizerSettings?>(null)

        init {
            viewModelScope.launch {
                val saved = repository.settings.first()
                local.update {
                    EqualizerUiState(saved.equalizer, saved.replayGain.clipProtection, isLoaded = true)
                }
                repository.settings.collect { settings ->
                    local.update { it.copy(clipProtection = settings.replayGain.clipProtection) }
                }
            }
            viewModelScope.launch { pending.filterNotNull().collect { repository.setEqualizer(it) } }
        }

        fun onEnabledChanged(enabled: Boolean) = change { it.copy(enabled = enabled) }

        fun onPresetChosen(preset: EqualizerPreset) = change { it.copy(preset = preset) }

        /** Band [index] dragged to [gainDb]; rounded to [Equalizer.STEP_DB]. */
        fun onBandChanged(
            index: Int,
            gainDb: Float,
        ) = change { it.withBand(index, gainDb) }

        /** Moves the preamp by [steps] of [Equalizer.STEP_DB]. */
        fun onPreampStepped(steps: Int) = change { it.copy(preampDb = it.preampDb + steps * Equalizer.STEP_DB) }

        /** Back to the flat curve without preamp; the custom curve is kept. */
        fun onReset() = change { it.copy(preset = EqualizerPreset.FLAT, preampDb = 0f) }

        private fun change(transform: (EqualizerSettings) -> EqualizerSettings) {
            if (!local.value.isLoaded) return
            val updated = transform(local.value.settings).clamped()
            if (updated == local.value.settings) return
            local.update { it.copy(settings = updated) }
            pending.value = updated
        }
    }
