package org.muzzzlo.player.feature.library

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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.muzzzlo.player.core.designsystem.component.TrackAction
import org.muzzzlo.player.core.model.Album
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.Track
import javax.inject.Inject

/** Navigation argument of the album card: the id of the album. */
const val ALBUM_ID_ARG = "albumId"

private const val STOP_TIMEOUT_MS = 5_000L

private fun <T> Flow<T>.stateIn(
    viewModel: ViewModel,
    initial: T,
): StateFlow<T> = stateIn(viewModel.viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)

private fun PlayerController.currentTrackId(): Flow<Long?> = state.map { it.currentTrack?.id }.distinctUntilChanged()

/**
 * What the home screen renders.
 *
 * @property continueAlbum album of the track in the restored queue, the one tile of "Continue listening".
 * Null hides the section. Recommendations from statistics arrive in 0.6.
 */
data class HomeUiState(
    val continueAlbum: Album? = null,
    val hasMusic: Boolean = true,
    val isScanning: Boolean = false,
)

/** Home screen (28:620): scanning, the entry points of the library and "Continue listening". */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val library: LibraryRepository,
        player: PlayerController,
    ) : ViewModel() {
        private val scanning = MutableStateFlow(false)
        private val currentAlbumId = player.state.map { it.currentTrack?.albumId }.distinctUntilChanged()

        val state: StateFlow<HomeUiState> =
            combine(
                library.observeAlbums(),
                library.observeTracks().map { it.isNotEmpty() }.distinctUntilChanged(),
                currentAlbumId,
                scanning,
            ) { albums, hasMusic, albumId, isScanning ->
                HomeUiState(albums.firstOrNull { it.id == albumId }, hasMusic, isScanning)
            }.stateIn(this, HomeUiState())

        /** Called once the audio permission is granted. Rescanning from the screen moves to the settings in 0.5.1. */
        fun onScanRequested() {
            if (scanning.value) return
            viewModelScope.launch {
                scanning.update { true }
                try {
                    library.scan()
                } finally {
                    scanning.update { false }
                }
            }
        }
    }

/** What the album list renders: albums already in the chosen order. */
data class AlbumListUiState(
    val albums: List<Album> = emptyList(),
    val sort: AlbumSort = AlbumSort(),
    val isLoaded: Boolean = false,
)

/** Album list (15:3) with the sort dialog (15:111). The sort survives process death, it is not a setting yet. */
@HiltViewModel
class AlbumListViewModel
    @Inject
    constructor(
        library: LibraryRepository,
        private val savedState: SavedStateHandle,
    ) : ViewModel() {
        private val sort = MutableStateFlow(restoredSort())

        val state: StateFlow<AlbumListUiState> =
            combine(library.observeAlbums(), sort) { albums, sort ->
                AlbumListUiState(albums.sortedBy(sort), sort, isLoaded = true)
            }.stateIn(this, AlbumListUiState())

        /** A sort option was tapped: the list follows at once, a second tap flips the direction. */
        fun onSortChosen(field: AlbumSortField) {
            val next = sort.value.choose(field)
            savedState[SORT_FIELD_KEY] = next.field.name
            savedState[SORT_ASCENDING_KEY] = next.ascending
            sort.value = next
        }

        private fun restoredSort(): AlbumSort {
            val name = savedState.get<String>(SORT_FIELD_KEY)
            val field = AlbumSortField.entries.firstOrNull { it.name == name } ?: return AlbumSort()
            return AlbumSort(field, savedState.get<Boolean>(SORT_ASCENDING_KEY) ?: field.ascendingByDefault)
        }

        private companion object {
            const val SORT_FIELD_KEY = "albumSortField"
            const val SORT_ASCENDING_KEY = "albumSortAscending"
        }
    }

/** What the album card renders. [album] is null while loading and after the album disappeared. */
data class AlbumUiState(
    val album: Album? = null,
    val tracks: List<Track> = emptyList(),
    val currentTrackId: Long? = null,
)

/** Album card (4:311). */
@HiltViewModel
class AlbumViewModel
    @Inject
    constructor(
        library: LibraryRepository,
        private val player: PlayerController,
        savedState: SavedStateHandle,
    ) : ViewModel() {
        private val albumId: Long = checkNotNull(savedState[ALBUM_ID_ARG])

        val state: StateFlow<AlbumUiState> =
            combine(
                library.observeAlbums().map { albums -> albums.firstOrNull { it.id == albumId } },
                library.observeAlbumTracks(albumId),
                player.currentTrackId(),
            ) { album, tracks, current -> AlbumUiState(album, tracks, current) }
                .stateIn(this, AlbumUiState())

        /** The "Play" button: the whole album from the first track. */
        fun onPlayAlbum() = onTrackClicked(0)

        fun onTrackClicked(index: Int) {
            val tracks = state.value.tracks
            if (index in tracks.indices) player.play(tracks, index)
        }

        fun onTrackAction(
            track: Track,
            action: TrackAction,
        ) = player.onTrackAction(track, action)
    }

/** What the list of all tracks renders. */
data class TracksUiState(
    val tracks: List<Track> = emptyList(),
    val currentTrackId: Long? = null,
    val isLoaded: Boolean = false,
)

/** All tracks of the library ("All songs" on the home screen; a screen without a mockup, DESIGN.md, section 7). */
@HiltViewModel
class TracksViewModel
    @Inject
    constructor(
        library: LibraryRepository,
        private val player: PlayerController,
    ) : ViewModel() {
        val state: StateFlow<TracksUiState> =
            combine(library.observeTracks(), player.currentTrackId()) { tracks, current ->
                TracksUiState(tracks, current, isLoaded = true)
            }.stateIn(this, TracksUiState())

        fun onTrackClicked(index: Int) {
            val tracks = state.value.tracks
            if (index in tracks.indices) player.play(tracks, index)
        }

        fun onTrackAction(
            track: Track,
            action: TrackAction,
        ) = player.onTrackAction(track, action)
    }
