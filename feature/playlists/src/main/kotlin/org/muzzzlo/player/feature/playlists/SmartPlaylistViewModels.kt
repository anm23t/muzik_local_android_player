package org.muzzzlo.player.feature.playlists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.muzzzlo.player.core.designsystem.component.AddToPlaylist
import org.muzzzlo.player.core.designsystem.component.ArtistChoice
import org.muzzzlo.player.core.designsystem.component.TrackAction
import org.muzzzlo.player.core.designsystem.component.TrackMarksMenu
import org.muzzzlo.player.core.model.CoverGradients
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.NameNormalizer
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.PlaylistNames
import org.muzzzlo.player.core.model.PlaylistRepository
import org.muzzzlo.player.core.model.SmartPlaylist
import org.muzzzlo.player.core.model.SmartPlaylistRepository
import org.muzzzlo.player.core.model.Track
import org.muzzzlo.player.core.model.TrackFacts
import org.muzzzlo.player.core.model.TrackFactsSource
import org.muzzzlo.player.core.model.TrackMarksRepository
import javax.inject.Inject

/** Navigation argument of the card and the editor of a smart playlist; 0 in the editor means a new one. */
const val SMART_PLAYLIST_ID_ARG = "smartPlaylistId"

/** How long the editor waits after a change before it counts the matching tracks again. */
internal const val COUNT_DELAY_MS = 300L

/** A smart playlist as its row in the list of playlists shows it, with its gradient cover. */
data class SmartPlaylistSummary(
    val id: Long,
    val name: String,
    val trackCount: Int,
) {
    val gradient: Int get() = CoverGradients.forSmart(id)
}

/**
 * What the card of a smart playlist renders (task 2.5.10).
 *
 * @property playlist null until loaded and after the playlist was deleted.
 * @property isDeleted the playlist was deleted from this card; the screen goes back.
 */
data class SmartPlaylistUiState(
    val playlist: SmartPlaylist? = null,
    val tracks: List<Track> = emptyList(),
    val currentTrackId: Long? = null,
    val isLoaded: Boolean = false,
    val isDeleted: Boolean = false,
) {
    val gradient: Int? get() = playlist?.let { CoverGradients.forSmart(it.id) }
}

/** The card of a smart playlist: its tracks by the rules, play, the track menu, "Edit rules" and "Delete". */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SmartPlaylistViewModel
    @Inject
    constructor(
        private val smart: SmartPlaylistRepository,
        playlists: PlaylistRepository,
        library: LibraryRepository,
        private val player: PlayerController,
        marks: TrackMarksRepository,
        savedState: SavedStateHandle,
    ) : ViewModel() {
        private val playlistId: Long = checkNotNull(savedState[SMART_PLAYLIST_ID_ARG])
        private val deleted = MutableStateFlow(false)
        private val menu = TrackMenuActions(playlists, library, player, marks, viewModelScope)

        val state: StateFlow<SmartPlaylistUiState> =
            combine(
                smart.observePlaylists().map { all -> all.firstOrNull { it.id == playlistId } }.distinctUntilChanged()
                    .flatMapLatest { playlist ->
                        playlist?.let { smart.observeTracks(it.rules).map { tracks -> playlist to tracks } }
                            ?: flowOf(null to emptyList())
                    },
                player.state.map { it.currentTrack?.id }.distinctUntilChanged(),
                deleted,
            ) { (playlist, tracks), current, isDeleted ->
                SmartPlaylistUiState(playlist, tracks, current, isLoaded = true, isDeleted = isDeleted)
            }.stateIn(this, SmartPlaylistUiState())

        /** The "Play" button: the whole playlist from the first track. */
        fun onPlay() = onTrackClicked(0)

        fun onTrackClicked(index: Int) {
            val tracks = state.value.tracks
            if (index in tracks.indices) player.play(tracks, index)
        }

        fun onDelete() {
            viewModelScope.launch {
                smart.delete(playlistId)
                deleted.value = true
            }
        }

        fun onTrackAction(
            track: Track,
            action: TrackAction,
        ) = menu.onTrackAction(track, action)

        suspend fun artistChoices(trackId: Long): List<ArtistChoice> = menu.artistChoices(trackId)

        fun addToPlaylist(trackId: Long): AddToPlaylist = menu.addToPlaylist(trackId)

        fun trackMarks(trackId: Long): TrackMarksMenu = menu.trackMarks(trackId)
    }

/** Names the editor offers for artist, album and genre conditions: what the library has, sorted by name. */
data class RuleChoices(
    val artists: List<String> = emptyList(),
    val albums: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
) {
    companion object {
        fun of(facts: List<TrackFacts>) =
            RuleChoices(
                artists = names(facts.flatMap { it.track.artists }),
                albums = names(facts.mapNotNull { it.track.albumTitle }),
                genres = names(facts.flatMap { it.genres }),
            )

        /** One spelling per [NameNormalizer.key] (the first met), sorted by that key. */
        private fun names(all: List<String>): List<String> =
            all.filter { it.isNotBlank() }
                .distinctBy(NameNormalizer::key)
                .sortedBy(NameNormalizer::key)
    }
}

/**
 * What the rules editor renders (task 2.5.10).
 *
 * @property matchingCount tracks the draft picks now; null until counted.
 * @property showErrors "Save" was pressed with a blank name or without conditions; the fields say what is missing.
 * @property savedId the id of the saved playlist; the screen leaves when it is set.
 */
data class SmartEditorUiState(
    val draft: SmartDraft = SmartDraft(),
    val isNew: Boolean = true,
    val isLoaded: Boolean = false,
    val matchingCount: Int? = null,
    val choices: RuleChoices = RuleChoices(),
    val showErrors: Boolean = false,
    val savedId: Long? = null,
) {
    val nameMissing: Boolean get() = PlaylistNames.clean(draft.name) == null
    val conditionsMissing: Boolean get() = draft.validConditionCount == 0
}

private data class EditorState(
    val draft: SmartDraft,
    val isLoaded: Boolean,
    val showErrors: Boolean = false,
    val savedId: Long? = null,
)

/** The editor of a smart playlist: a new one ([SMART_PLAYLIST_ID_ARG] 0) or the rules of an existing one. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SmartPlaylistEditorViewModel
    @Inject
    constructor(
        private val smart: SmartPlaylistRepository,
        facts: TrackFactsSource,
        savedState: SavedStateHandle,
    ) : ViewModel() {
        private val playlistId: Long = savedState[SMART_PLAYLIST_ID_ARG] ?: 0L
        private val editor =
            MutableStateFlow(EditorState(SmartDraft.new(), isLoaded = playlistId == 0L))

        private val matchingCount =
            editor.map { it.draft.toRules() }
                .distinctUntilChanged()
                .debounce(COUNT_DELAY_MS)
                .flatMapLatest { rules -> smart.observeTracks(rules).map<List<Track>, Int?> { it.size } }
                .onStart { emit(null) }

        val state: StateFlow<SmartEditorUiState> =
            combine(editor, matchingCount, facts.observeFacts().map(RuleChoices::of).onStart { emit(RuleChoices()) }) {
                    editor, count, choices ->
                SmartEditorUiState(
                    draft = editor.draft,
                    isNew = playlistId == 0L,
                    isLoaded = editor.isLoaded,
                    matchingCount = count,
                    choices = choices,
                    showErrors = editor.showErrors,
                    savedId = editor.savedId,
                )
            }.stateIn(this, SmartEditorUiState(isNew = playlistId == 0L))

        init {
            if (playlistId != 0L) {
                viewModelScope.launch {
                    val playlist = smart.playlist(playlistId)
                    val draft = playlist?.let { SmartDraft.of(it.name, it.rules) } ?: SmartDraft.new()
                    editor.update { it.copy(draft = draft, isLoaded = true) }
                }
            }
        }

        fun onEdit(edit: DraftEdit) = editor.update { it.copy(draft = edit.applyTo(it.draft)) }

        /** Saves the draft; a blank name or no condition shows the errors instead. */
        fun onSave() {
            val current = editor.value
            val name = PlaylistNames.clean(current.draft.name)
            if (name == null || current.draft.validConditionCount == 0 || current.savedId != null) {
                editor.update { it.copy(showErrors = true) }
                return
            }
            val rules = current.draft.toRules()
            viewModelScope.launch {
                val id =
                    if (playlistId == 0L) {
                        smart.create(name, rules)
                    } else {
                        smart.update(playlistId, name, rules)
                        playlistId
                    }
                editor.update { it.copy(savedId = id) }
            }
        }
    }
