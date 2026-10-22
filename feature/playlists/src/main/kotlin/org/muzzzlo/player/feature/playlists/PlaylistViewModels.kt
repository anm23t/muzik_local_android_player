package org.muzzzlo.player.feature.playlists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.muzzzlo.player.core.designsystem.component.AddToPlaylist
import org.muzzzlo.player.core.designsystem.component.ArtistChoice
import org.muzzzlo.player.core.designsystem.component.PlaylistChoice
import org.muzzzlo.player.core.designsystem.component.TrackAction
import org.muzzzlo.player.core.designsystem.component.TrackMarksMenu
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.Playlist
import org.muzzzlo.player.core.model.PlaylistEntry
import org.muzzzlo.player.core.model.PlaylistNames
import org.muzzzlo.player.core.model.PlaylistOrder
import org.muzzzlo.player.core.model.PlaylistRepository
import org.muzzzlo.player.core.model.Track
import org.muzzzlo.player.core.model.TrackMarksRepository
import javax.inject.Inject

/** Navigation argument of the playlist card. */
const val PLAYLIST_ID_ARG = "playlistId"

private const val STOP_TIMEOUT_MS = 5_000L

private fun <T> Flow<T>.stateIn(
    viewModel: ViewModel,
    initial: T,
): StateFlow<T> = stateIn(viewModel.viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)

/** What the list of playlists renders. */
data class PlaylistListUiState(
    val playlists: List<Playlist> = emptyList(),
    val isLoaded: Boolean = false,
)

/** The list of playlists (button "Playlists" of the home screen) and the "New playlist" button. */
@HiltViewModel
class PlaylistListViewModel
    @Inject
    constructor(
        private val playlists: PlaylistRepository,
    ) : ViewModel() {
        val state: StateFlow<PlaylistListUiState> =
            playlists.observePlaylists()
                .map { PlaylistListUiState(it, isLoaded = true) }
                .stateIn(this, PlaylistListUiState())

        /** Creates an empty playlist; a blank name does nothing. */
        fun onCreate(name: String) {
            val clean = PlaylistNames.clean(name) ?: return
            viewModelScope.launch { playlists.create(clean) }
        }
    }

/**
 * What the playlist card renders.
 *
 * @property playlist null until loaded and after the playlist was deleted.
 */
data class PlaylistUiState(
    val playlist: Playlist? = null,
    val entries: List<PlaylistEntry> = emptyList(),
    val currentTrackId: Long? = null,
    val isLoaded: Boolean = false,
) {
    val tracks: List<Track> get() = entries.map { it.track }
}

/** The playlist card: play, reorder, remove entries, rename and delete the playlist. */
@HiltViewModel
class PlaylistViewModel
    @Inject
    constructor(
        private val playlists: PlaylistRepository,
        private val library: LibraryRepository,
        private val player: PlayerController,
        private val marks: TrackMarksRepository,
        savedState: SavedStateHandle,
    ) : ViewModel() {
        private val playlistId: Long = checkNotNull(savedState[PLAYLIST_ID_ARG])

        /** Order shown while a move is being saved, so the row does not jump back for a frame. */
        private val pendingOrder = MutableStateFlow<List<Long>?>(null)

        val state: StateFlow<PlaylistUiState> =
            combine(
                playlists.observePlaylist(playlistId),
                playlists.observeEntries(playlistId),
                pendingOrder,
                player.state.map { it.currentTrack?.id }.distinctUntilChanged(),
            ) { playlist, entries, pending, current ->
                PlaylistUiState(playlist, ordered(entries, pending), current, isLoaded = true)
            }.stateIn(this, PlaylistUiState())

        /** The "Play" button: the whole playlist from the first track. */
        fun onPlay() = onTrackClicked(0)

        fun onTrackClicked(index: Int) {
            val tracks = state.value.tracks
            if (index !in tracks.indices) return
            player.play(tracks, index, playlistId)
            viewModelScope.launch { playlists.markPlayed(playlistId) }
        }

        /** A row was dragged from [from] to [to] (indexes of the visible entries). */
        fun onMoved(
            from: Int,
            to: Int,
        ) {
            val ids = state.value.entries.map { it.entryId }
            val moved = PlaylistOrder.move(ids, from, to)
            if (moved == ids) return
            pendingOrder.value = moved
            viewModelScope.launch {
                playlists.reorder(playlistId, moved)
                pendingOrder.value = null
            }
        }

        fun onTrackAction(
            entry: PlaylistEntry,
            action: TrackAction,
        ) {
            when (action) {
                TrackAction.PlayNext -> player.playNext(entry.track)
                TrackAction.AddToQueue -> player.addToQueue(entry.track)
                TrackAction.RemoveFromPlaylist -> viewModelScope.launch { playlists.removeEntry(entry.entryId) }
                // Navigation, pickers, favorites and rating are handled by the screen and the menu.
                TrackAction.OpenQueue,
                TrackAction.GoToArtist,
                TrackAction.AddToPlaylist,
                TrackAction.AddToFavorites,
                TrackAction.Rate,
                TrackAction.EditTags,
                -> Unit
            }
        }

        fun onRename(name: String) {
            val clean = PlaylistNames.clean(name) ?: return
            viewModelScope.launch { playlists.rename(playlistId, clean) }
        }

        fun onDelete() {
            viewModelScope.launch { playlists.delete(playlistId) }
        }

        /** Artists of the track for the track menu ("Go to artist"). */
        suspend fun artistChoices(trackId: Long): List<ArtistChoice> =
            library.artistsOfTrack(trackId).map { ArtistChoice(it.id, it.name, it.photoPath) }

        /** "Add to playlist" of the track menu; this playlist is offered too, a track may be in it twice. */
        fun addToPlaylist(trackId: Long): AddToPlaylist =
            AddToPlaylist(
                load = { playlists.observePlaylists().first().map { PlaylistChoice(it.id, it.name) } },
                onAdd = { id -> viewModelScope.launch { playlists.addTracks(id, listOf(trackId)) } },
                onCreate = { name ->
                    PlaylistNames.clean(name)?.let { clean ->
                        viewModelScope.launch { playlists.createWithTracks(clean, listOf(trackId)) }
                    }
                },
            )

        /** "Add to favorites" and "Rate" of the track menu. */
        fun trackMarks(trackId: Long): TrackMarksMenu = marks.menuFor(viewModelScope, trackId)

        private fun ordered(
            entries: List<PlaylistEntry>,
            pending: List<Long>?,
        ): List<PlaylistEntry> {
            if (pending == null) return entries
            val byId = entries.associateBy { it.entryId }
            val inOrder = pending.mapNotNull(byId::get)
            // The database changed meanwhile (an entry added or removed): show it as it is.
            return if (inOrder.size == entries.size) inOrder else entries
        }
    }
