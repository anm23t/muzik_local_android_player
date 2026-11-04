package org.muzzzlo.player

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import org.muzzzlo.player.core.model.BackgroundPaletteRepository
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.SettingsRepository
import javax.inject.Inject

/**
 * What the live background shows (task 2.0.8).
 *
 * @property enabled the setting "Animated background"; off is a black background.
 * @property palette colors of the current track (also on pause), null when the queue is empty.
 */
data class LiveBackgroundUiState(
    val enabled: Boolean = true,
    val palette: List<Color>? = null,
)

/** Screens with a black background whatever plays: the settings with their sub-screens and the statistics. */
internal val BLACK_BACKGROUND_ROUTES =
    setOf("settings", "settingsFolders", "settingsEqualizer", "settingsQuarantine", "stats")

/**
 * The rule of the live background (task 2.0.8): colored when it is switched on, a track is current (also on pause)
 * and the screen is not one of [BLACK_BACKGROUND_ROUTES]. The player layer passes no route: it is always colored
 * then.
 */
internal fun backgroundColored(
    state: LiveBackgroundUiState,
    route: String?,
): Boolean = state.enabled && state.palette != null && route !in BLACK_BACKGROUND_ROUTES

/**
 * Colors of the live background: the palette of the cover of the current track, or of the default red when the
 * dynamic accent is off. A new palette replaces the old one only when ready, so the screen fades from one to the other.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LiveBackgroundViewModel
    @Inject
    constructor(
        player: PlayerController,
        settings: SettingsRepository,
        palettes: BackgroundPaletteRepository,
    ) : ViewModel() {
        val state: StateFlow<LiveBackgroundUiState> =
            combine(
                player.state.map { it.currentTrack?.id }.distinctUntilChanged(),
                settings.settings.map { it.dynamicAccent to it.animatedBackground }.distinctUntilChanged(),
            ) { trackId, (dynamic, enabled) -> Triple(trackId, dynamic, enabled) }
                .mapLatest { (trackId, dynamic, enabled) ->
                    val colors =
                        when {
                            trackId == null -> null
                            dynamic -> palettes.paletteOf(trackId)
                            else -> palettes.defaultPalette
                        }
                    LiveBackgroundUiState(enabled, colors?.map(::Color))
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LiveBackgroundUiState())

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
