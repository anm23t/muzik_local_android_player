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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.muzzzlo.player.core.designsystem.component.AddToPlaylist
import org.muzzzlo.player.core.designsystem.component.ArtistChoice
import org.muzzzlo.player.core.designsystem.component.PlaylistChoice
import org.muzzzlo.player.core.designsystem.component.TrackAction
import org.muzzzlo.player.core.designsystem.component.TrackMarksMenu
import org.muzzzlo.player.core.model.AutoPlaylist
import org.muzzzlo.player.core.model.AutoPlaylistRepository
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.Playlist
import org.muzzzlo.player.core.model.PlaylistEntry
import org.muzzzlo.player.core.model.PlaylistImport
import org.muzzzlo.player.core.model.PlaylistNames
import org.muzzzlo.player.core.model.PlaylistOrder
import org.muzzzlo.player.core.model.PlaylistRepository
import org.muzzzlo.player.core.model.PlaylistTransfer
import org.muzzzlo.player.core.model.Track
import org.muzzzlo.player.core.model.TrackMarksRepository
import java.io.IOException
import javax.inject.Inject

/** Navigation argument of the playlist card. */
const val PLAYLIST_ID_ARG = "playlistId"

private const val STOP_TIMEOUT_MS = 5_000L

private fun <T> Flow<T>.stateIn(
    viewModel: ViewModel,
    initial: T,
): StateFlow<T> = stateIn(viewModel.viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)

/** An automatic playlist as its row in the list shows it. */
data class AutoPlaylistSummary(
    val playlist: AutoPlaylist,
    val trackCount: Int,
    val firstTrackId: Long?,
)

/** The result of an import or export of a playlist file, shown in a dialog until dismissed. */
sealed interface PlaylistFileMessage {
    data class Imported(
        val result: PlaylistImport,
    ) : PlaylistFileMessage

    data object Exported : PlaylistFileMessage

    /** The file could not be opened, read or written. */
    data object FileError : PlaylistFileMessage
}

/** Runs a file operation; a file failure becomes [PlaylistFileMessage.FileError], anything else goes on up. */
private suspend fun fileTask(action: suspend () -> PlaylistFileMessage): PlaylistFileMessage =
    try {
        action()
    } catch (_: IOException) {
        PlaylistFileMessage.FileError
    } catch (_: SecurityException) {
        PlaylistFileMessage.FileError
    }

/** What the list of playlists renders: the automatic playlists first, then the user's own. */
data class PlaylistListUiState(
    val playlists: List<Playlist> = emptyList(),
    val auto: List<AutoPlaylistSummary> = emptyList(),
    val isLoaded: Boolean = false,
    val busy: Boolean = false,
    val message: PlaylistFileMessage? = null,
)

/** A running import or export and its result. */
private data class FileState(
    val busy: Boolean = false,
    val message: PlaylistFileMessage? = null,
)

/** The list of playlists (button "Playlists" of the home screen), "New playlist" and the import of M3U files. */
@HiltViewModel
class PlaylistListViewModel
    @Inject
    constructor(
        private val playlists: PlaylistRepository,
        autoPlaylists: AutoPlaylistRepository,
        private val transfer: PlaylistTransfer,
    ) : ViewModel() {
        private val file = MutableStateFlow(FileState())

        val state: StateFlow<PlaylistListUiState> =
            combine(
                playlists.observePlaylists(),
                combine(AutoPlaylist.entries.map { kind -> autoPlaylists.observeTracks(kind).map { kind to it } }) {
                    it.map { (kind, tracks) -> AutoPlaylistSummary(kind, tracks.size, tracks.firstOrNull()?.id) }
                },
                file,
            ) { user, auto, file -> PlaylistListUiState(user, auto, isLoaded = true, file.busy, file.message) }
                .stateIn(this, PlaylistListUiState())

        /** Creates an empty playlist; a blank name does nothing. */
        fun onCreate(name: String) {
            val clean = PlaylistNames.clean(name) ?: return
            viewModelScope.launch { playlists.create(clean) }
        }

        /** The user picked an M3U file; [fallbackName] names the playlist when the file name gives none. */
        fun onImport(
            uri: String,
            fallbackName: String,
        ) {
            if (file.value.busy) return
            file.value = FileState(busy = true)
            viewModelScope.launch {
                val message = fileTask { PlaylistFileMessage.Imported(transfer.import(uri, fallbackName)) }
                file.value = FileState(message = message)
            }
        }

        fun onMessageDismissed() = file.update { it.copy(message = null) }
    }

/** Navigation argument of the card of an automatic playlist: [AutoPlaylist.key]. */
const val AUTO_PLAYLIST_ARG = "autoPlaylist"

/**
 * What the card of an automatic playlist renders.
 *
 * @property playlist null for an unknown key in the route.
 */
data class AutoPlaylistUiState(
    val playlist: AutoPlaylist? = null,
    val tracks: List<Track> = emptyList(),
    val currentTrackId: Long? = null,
    val isLoaded: Boolean = false,
)

/** The card of "Favorites" or "Top songs": play and the track menu, nothing to edit. */
@HiltViewModel
class AutoPlaylistViewModel
    @Inject
    constructor(
        autoPlaylists: AutoPlaylistRepository,
        private val playlists: PlaylistRepository,
        private val library: LibraryRepository,
        private val player: PlayerController,
        private val marks: TrackMarksRepository,
        savedState: SavedStateHandle,
    ) : ViewModel() {
        private val playlist: AutoPlaylist? = AutoPlaylist.fromKey(savedState[AUTO_PLAYLIST_ARG])

        val state: StateFlow<AutoPlaylistUiState> =
            combine(
                playlist?.let(autoPlaylists::observeTracks) ?: flowOf(emptyList()),
                player.state.map { it.currentTrack?.id }.distinctUntilChanged(),
            ) { tracks, current -> AutoPlaylistUiState(playlist, tracks, current, isLoaded = true) }
                .stateIn(this, AutoPlaylistUiState())

        /** The "Play" button: the whole playlist from the first track. */
        fun onPlay() = onTrackClicked(0)

        fun onTrackClicked(index: Int) {
            val tracks = state.value.tracks
            if (index in tracks.indices) player.play(tracks, index)
        }

        fun onTrackAction(
            track: Track,
            action: TrackAction,
        ) {
            when (action) {
                TrackAction.PlayNext -> player.playNext(track)
                TrackAction.AddToQueue -> player.addToQueue(track)
                // Navigation, pickers, favorites and rating are handled by the screen and the menu.
                TrackAction.OpenQueue,
                TrackAction.GoToArtist,
                TrackAction.AddToPlaylist,
                TrackAction.RemoveFromPlaylist,
                TrackAction.AddToFavorites,
                TrackAction.Rate,
                TrackAction.EditTags,
                TrackAction.Share,
                TrackAction.SleepTimer,
                -> Unit
            }
        }

        /** Artists of the track for the track menu ("Go to artist"). */
        suspend fun artistChoices(trackId: Long): List<ArtistChoice> =
            library.artistsOfTrack(trackId).map { ArtistChoice(it.id, it.name, it.photoPath) }

        /** "Add to playlist" of the track menu. */
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
    val message: PlaylistFileMessage? = null,
) {
    val tracks: List<Track> get() = entries.map { it.track }
}

/** The playlist card: play, reorder, remove entries, rename, delete and export the playlist. */
@HiltViewModel
class PlaylistViewModel
    @Inject
    constructor(
        private val playlists: PlaylistRepository,
        private val library: LibraryRepository,
        private val player: PlayerController,
        private val marks: TrackMarksRepository,
        private val transfer: PlaylistTransfer,
        savedState: SavedStateHandle,
    ) : ViewModel() {
        private val playlistId: Long = checkNotNull(savedState[PLAYLIST_ID_ARG])

        /** Order shown while a move is being saved, so the row does not jump back for a frame. */
        private val pendingOrder = MutableStateFlow<List<Long>?>(null)

        private val file = MutableStateFlow(FileState())

        val state: StateFlow<PlaylistUiState> =
            combine(
                playlists.observePlaylist(playlistId),
                playlists.observeEntries(playlistId),
                pendingOrder,
                player.state.map { it.currentTrack?.id }.distinctUntilChanged(),
                file,
            ) { playlist, entries, pending, current, file ->
                PlaylistUiState(playlist, ordered(entries, pending), current, isLoaded = true, file.message)
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
                TrackAction.Share,
                TrackAction.SleepTimer,
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

        /** The user chose where to save the playlist as M3U8. */
        fun onExport(uri: String) {
            if (file.value.busy) return
            file.value = FileState(busy = true)
            viewModelScope.launch {
                val message =
                    fileTask {
                        transfer.export(playlistId, uri)
                        PlaylistFileMessage.Exported
                    }
                file.value = FileState(message = message)
            }
        }

        fun onMessageDismissed() = file.update { it.copy(message = null) }

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
