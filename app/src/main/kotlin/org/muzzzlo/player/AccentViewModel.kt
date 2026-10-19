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
import org.muzzzlo.player.core.designsystem.theme.DefaultAccent
import org.muzzzlo.player.core.model.AccentRepository
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.SettingsRepository
import javax.inject.Inject

/**
 * The accent of the whole app: it follows the cover of the playing track (DESIGN.md, section 4) and is the default
 * red when nothing plays, the cover gives no color or the dynamic accent is switched off in the settings.
 * While a new color is computed the previous one stays, so the theme never flashes through the default.
 */
@HiltViewModel
class AccentViewModel
    @Inject
    constructor(
        player: PlayerController,
        accents: AccentRepository,
        settings: SettingsRepository,
    ) : ViewModel() {
        @OptIn(ExperimentalCoroutinesApi::class)
        val accent: StateFlow<Color> =
            combine(
                player.state.map { it.currentTrack?.id },
                settings.settings.map { it.dynamicAccent },
            ) { id, dynamic ->
                id.takeIf { dynamic }
            }
                .distinctUntilChanged()
                .mapLatest { trackId -> trackId?.let { accents.accentOf(it) }?.let(::Color) ?: DefaultAccent }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DefaultAccent)

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
