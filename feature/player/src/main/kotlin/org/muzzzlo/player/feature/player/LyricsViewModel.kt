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
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.muzzzlo.player.core.model.Lyrics
import org.muzzzlo.player.core.model.LyricsRepository
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.SettingsRepository
import org.muzzzlo.player.core.model.Track
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

/**
 * The lyrics part of the big player (task 2.0.7).
 *
 * @property lyrics the lyrics of the playing track; null while they load or when it has none.
 * @property isLoading the lyrics of a new track are being read.
 * @property showLyrics the player shows the lyrics instead of the cover.
 */
data class LyricsUiState(
    val lyrics: Lyrics? = null,
    val isLoading: Boolean = false,
    val showLyrics: Boolean = false,
) {
    /** The lyrics button works and the swipe up opens the lyrics. */
    val available: Boolean get() = lyrics != null
}

/** Lyrics of one track as they are being read. */
private data class LoadedLyrics(
    val lyrics: Lyrics?,
    val isLoading: Boolean,
)

/**
 * Whether the player shows the lyrics: the user wants them ([wanted]) and the track has them. While the lyrics of a
 * new track load, the player keeps what it showed ([wasShowing]), so going from one track with lyrics to the next
 * does not flash the cover.
 */
internal fun showsLyrics(
    wanted: Boolean,
    loaded: Boolean,
    hasLyrics: Boolean,
    wasShowing: Boolean,
): Boolean = wanted && if (loaded) hasLyrics else wasShowing

/**
 * Reads the lyrics of the playing track while the big player is open and keeps the lyrics mode. The mode is a
 * setting, so it stays when the track changes and after the player closes; a track without lyrics shows the cover,
 * and the next track with lyrics brings them back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LyricsViewModel
    @Inject
    constructor(
        private val player: PlayerController,
        private val settings: SettingsRepository,
        private val repository: LyricsRepository,
    ) : ViewModel() {
        private val loaded: Flow<LoadedLyrics> =
            player.state
                .map { it.currentTrack }
                .distinctUntilChanged { a, b -> a?.id == b?.id && a?.uri == b?.uri }
                .flatMapLatest(::lyricsOf)

        val state: StateFlow<LyricsUiState> =
            combine(loaded, settings.settings.map { it.lyricsShown }.distinctUntilChanged()) { lyrics, wanted ->
                lyrics to wanted
            }.runningFold(LyricsUiState()) { previous, (lyrics, wanted) ->
                LyricsUiState(
                    lyrics = lyrics.lyrics,
                    isLoading = lyrics.isLoading,
                    showLyrics = showsLyrics(wanted, !lyrics.isLoading, lyrics.lyrics != null, previous.showLyrics),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LyricsUiState())

        /** The lyrics button and the miniature cover: switches between the lyrics and the cover. */
        fun onToggle() {
            val current = state.value
            if (!current.showLyrics && !current.available) return
            viewModelScope.launch { settings.setLyricsShown(!current.showLyrics) }
        }

        /** A swipe up over the cover opens the lyrics; false when the track has none, so the cover bounces back. */
        fun onSwipeUp(): Boolean {
            if (!state.value.available) return false
            viewModelScope.launch { settings.setLyricsShown(true) }
            return true
        }

        /** A tap on a synchronised line jumps to it. */
        fun onLineTapped(timeMs: Long) = player.seekTo(timeMs)

        private fun lyricsOf(track: Track?): Flow<LoadedLyrics> =
            if (track == null) {
                flowOf(LoadedLyrics(null, isLoading = false))
            } else {
                flow {
                    emit(LoadedLyrics(null, isLoading = true))
                    emit(LoadedLyrics(repository.lyricsOf(track.id)?.lyrics, isLoading = false))
                }
            }
    }
