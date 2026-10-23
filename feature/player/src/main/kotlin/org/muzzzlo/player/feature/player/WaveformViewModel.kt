package org.muzzzlo.player.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.SettingsRepository
import org.muzzzlo.player.core.model.Track
import org.muzzzlo.player.core.model.WaveformPeaks
import org.muzzzlo.player.core.model.WaveformRepository
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

/**
 * Peaks of the playing track for the waveform of the big player (task 1.2.7). Null while the setting is off, there
 * is no track, the peaks are being computed or the file cannot be decoded: the player then shows the plain bar.
 * A new track starts from null, so the bar of the old track never stays under the new one; a change of the track
 * metadata (a rating, an edit of tags) keeps the waveform. The work stops a few seconds after the player closes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WaveformViewModel
    @Inject
    constructor(
        player: PlayerController,
        settings: SettingsRepository,
        private val waveforms: WaveformRepository,
    ) : ViewModel() {
        val peaks: StateFlow<WaveformPeaks?> =
            combine(
                settings.settings.map { it.waveform }.distinctUntilChanged(),
                player.state.map { it.currentTrack }.distinctUntilChanged { a, b -> a?.uri == b?.uri },
            ) { enabled, track -> track.takeIf { enabled } }
                .distinctUntilChanged { a, b -> a?.uri == b?.uri }
                .flatMapLatest(::peaksOf)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        private fun peaksOf(track: Track?): Flow<WaveformPeaks?> =
            if (track == null) {
                flowOf(null)
            } else {
                flow {
                    emit(null)
                    emit(waveforms.peaks(track))
                }
            }
    }
