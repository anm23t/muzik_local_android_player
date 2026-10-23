package org.muzzzlo.player.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import org.muzzzlo.player.core.designsystem.component.AddToPlaylist
import org.muzzzlo.player.core.designsystem.component.ArtistChoice
import org.muzzzlo.player.core.designsystem.component.TrackAction
import org.muzzzlo.player.core.designsystem.component.TrackMarksMenu
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.PlaylistRepository
import org.muzzzlo.player.core.model.SearchRepository
import org.muzzzlo.player.core.model.SearchResults
import org.muzzzlo.player.core.model.Track
import org.muzzzlo.player.core.model.TrackMarksRepository
import javax.inject.Inject

/** Results show up this long after the last typed letter, so a fast typist does not search every letter. */
internal const val SEARCH_DEBOUNCE_MS = 150L

private const val QUERY_KEY = "query"
private const val SEARCH_STOP_TIMEOUT_MS = 5_000L

/**
 * What the search screen renders.
 *
 * @property query the text in the field, as typed.
 * @property results results for the query of [resultsFor]; empty for a blank query.
 * @property resultsFor the query [results] belong to; it lags behind [query] while the user types.
 */
data class SearchUiState(
    val query: String = "",
    val results: SearchResults = SearchResults(),
    val resultsFor: String = "",
    val currentTrackId: Long? = null,
) {
    /** The field is blank: the screen shows the hint instead of results. */
    val isBlank: Boolean get() = query.isBlank()

    /** Results for the current query came and there are none. */
    val nothingFound: Boolean get() = !isBlank && resultsFor == query && results.isEmpty
}

/**
 * Search over the library (task 1.2.4): songs by title and by each artist, albums, artists with their old spellings,
 * playlists ([org.muzzzlo.player.core.model.SearchIndex]). The query survives the trip to a found album and back.
 */
@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        search: SearchRepository,
        private val library: LibraryRepository,
        private val player: PlayerController,
        private val playlists: PlaylistRepository,
        private val marks: TrackMarksRepository,
        dispatchers: DispatcherProvider,
        private val savedState: SavedStateHandle,
    ) : ViewModel() {
        private val query = savedState.getStateFlow(QUERY_KEY, "")

        @OptIn(FlowPreview::class)
        private val results =
            combine(search.observeIndex(), query.debounce(SEARCH_DEBOUNCE_MS)) { index, typed ->
                typed to index.search(typed)
            }.flowOn(dispatchers.default)
                // The field shows every letter at once, without waiting for the index and the pause.
                .onStart { emit("" to SearchResults()) }

        private val currentTrackId = player.state.map { it.currentTrack?.id }.distinctUntilChanged()

        val state: StateFlow<SearchUiState> =
            combine(query, results, currentTrackId) { typed, (searched, found), current ->
                SearchUiState(query = typed, results = found, resultsFor = searched, currentTrackId = current)
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(SEARCH_STOP_TIMEOUT_MS),
                SearchUiState(query = query.value),
            )

        fun onQueryChanged(text: String) {
            savedState[QUERY_KEY] = text
        }

        /** Plays the found songs from the tapped one. */
        fun onTrackClicked(index: Int) {
            val tracks = state.value.results.tracks
            if (index in tracks.indices) player.play(tracks, index)
        }

        fun onTrackAction(
            track: Track,
            action: TrackAction,
        ) = player.onTrackAction(track, action)

        /** Artists of the track for the track menu ("Go to artist"). */
        suspend fun artistChoices(trackId: Long): List<ArtistChoice> = library.artistChoices(trackId)

        /** "Add to playlist" of the track menu. */
        fun addToPlaylist(trackId: Long): AddToPlaylist = playlists.addToPlaylist(viewModelScope, trackId)

        /** "Add to favorites" and "Rate" of the track menu. */
        fun trackMarks(trackId: Long): TrackMarksMenu = marks.menuFor(viewModelScope, trackId)
    }
