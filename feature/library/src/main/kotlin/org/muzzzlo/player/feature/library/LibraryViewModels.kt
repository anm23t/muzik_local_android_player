package org.muzzzlo.player.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.muzzzlo.player.core.designsystem.component.ArtistChoice
import org.muzzzlo.player.core.designsystem.component.TrackAction
import org.muzzzlo.player.core.model.Album
import org.muzzzlo.player.core.model.Artist
import org.muzzzlo.player.core.model.ArtistDiscography
import org.muzzzlo.player.core.model.CompoundNameProblem
import org.muzzzlo.player.core.model.CompoundRepository
import org.muzzzlo.player.core.model.CompoundRules
import org.muzzzlo.player.core.model.ContinueListening
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.StatsRepository
import org.muzzzlo.player.core.model.Track
import javax.inject.Inject

/** Navigation argument of the album card: the id of the album. */
const val ALBUM_ID_ARG = "albumId"

/** Navigation argument of the artist card and of the artist's songs: the id of the artist. */
const val ARTIST_ID_ARG = "artistId"

/** Navigation argument of the artist card opened by name (player, album card). */
const val ARTIST_NAME_ARG = "artistName"

/** Value of an optional [ARTIST_ID_ARG] that means "no artist": the list of all tracks, or the card by name. */
const val NO_ARTIST = -1L

private const val STOP_TIMEOUT_MS = 5_000L

private fun <T> Flow<T>.stateIn(
    viewModel: ViewModel,
    initial: T,
): StateFlow<T> = stateIn(viewModel.viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)

private fun PlayerController.currentTrackId(): Flow<Long?> = state.map { it.currentTrack?.id }.distinctUntilChanged()

/**
 * What the home screen renders.
 *
 * @property continueAlbums tiles of "Continue listening": up to five albums of the latest listens
 * ([ContinueListening]), the album of the restored queue while there is no history. Empty hides the section.
 */
data class HomeUiState(
    val continueAlbums: List<Album> = emptyList(),
    val hasMusic: Boolean = true,
    val isScanning: Boolean = false,
)

/** Home screen (28:620): scanning, the entry points of the library and "Continue listening". */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val library: LibraryRepository,
        stats: StatsRepository,
        player: PlayerController,
    ) : ViewModel() {
        private val scanning = MutableStateFlow(false)
        private val currentAlbumId = player.state.map { it.currentTrack?.albumId }.distinctUntilChanged()

        val state: StateFlow<HomeUiState> =
            combine(
                library.observeAlbums(),
                library.observeTracks().map { it.isNotEmpty() }.distinctUntilChanged(),
                currentAlbumId,
                stats.recentAlbumIds(ContinueListening.MAX_TILES),
                scanning,
            ) { albums, hasMusic, albumId, recent, isScanning ->
                val queueAlbum = albums.firstOrNull { it.id == albumId }
                HomeUiState(ContinueListening.albums(recent, albums, queueAlbum), hasMusic, isScanning)
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
    val sort: AlbumSort = DefaultAlbumSort,
    val isLoaded: Boolean = false,
)

/** Album list (15:3) with the sort dialog (15:111). */
@HiltViewModel
class AlbumListViewModel
    @Inject
    constructor(
        library: LibraryRepository,
        savedState: SavedStateHandle,
    ) : ViewModel() {
        private val sort = SavedSort(savedState, "albumSort", AlbumSortField.entries, DefaultAlbumSort)

        val state: StateFlow<AlbumListUiState> =
            combine(library.observeAlbums(), sort.sort) { albums, sort ->
                AlbumListUiState(albums.sortedBy(sort), sort, isLoaded = true)
            }.stateIn(this, AlbumListUiState())

        /** A sort option was tapped: the list follows at once, a second tap flips the direction. */
        fun onSortChosen(field: AlbumSortField) = sort.choose(field)
    }

/**
 * The "Remember as one artist" dialog (no mockup, DESIGN.md, section 7).
 *
 * @property parts the selected artists in the order the name will be written.
 * @property name the compound name as typed, starts as the parts joined by a comma.
 * @property problem why [name] cannot be saved yet; null while it is fine or nothing was tried.
 * @property duplicate the name is already a rule.
 */
data class RememberState(
    val parts: List<String>,
    val name: String,
    val problem: CompoundNameProblem? = null,
    val duplicate: Boolean = false,
    val isSaving: Boolean = false,
)

/**
 * What the artist list renders: artists already in the chosen order.
 *
 * @property selecting the multiple choice mode is on, a tap ticks a row instead of opening the artist.
 * @property selectedIds ticked artists in the order they were ticked.
 */
data class ArtistListUiState(
    val artists: List<Artist> = emptyList(),
    val sort: ArtistSort = DefaultArtistSort,
    val isLoaded: Boolean = false,
    val selecting: Boolean = false,
    val selectedIds: List<Long> = emptyList(),
    val remember: RememberState? = null,
)

/** Artist list (23:403): the same screen as the album list with other rows (DESIGN.md, section 3). */
@HiltViewModel
class ArtistListViewModel
    @Inject
    constructor(
        library: LibraryRepository,
        private val compounds: CompoundRepository,
        savedState: SavedStateHandle,
    ) : ViewModel() {
        private val sort = SavedSort(savedState, "artistSort", ArtistSortField.entries, DefaultArtistSort)
        private val selection = MutableStateFlow(Selection())
        private val artists = library.observeArtists().stateIn(this, emptyList())

        val state: StateFlow<ArtistListUiState> =
            combine(artists, sort.sort, selection) { artists, sort, selection ->
                ArtistListUiState(
                    artists = artists.sortedBy(sort),
                    sort = sort,
                    isLoaded = true,
                    selecting = selection.selecting,
                    selectedIds = selection.ids,
                    remember = selection.remember,
                )
            }.stateIn(this, ArtistListUiState())

        /** A sort option was tapped: the list follows at once, a second tap flips the direction. */
        fun onSortChosen(field: ArtistSortField) = sort.choose(field)

        /** The choice mode button: turns the mode on, or off with the ticks cleared. */
        fun onSelectionToggled() = selection.update { if (it.selecting) Selection() else Selection(selecting = true) }

        /** A row was tapped in the choice mode: ticks it, or unticks it. */
        fun onArtistTicked(artistId: Long) =
            selection.update { it.copy(ids = if (artistId in it.ids) it.ids - artistId else it.ids + artistId) }

        /** "Remember as one artist": opens the dialog for two or more ticked artists. */
        fun onRememberClicked() {
            val names = selection.value.ids.mapNotNull { id -> artists.value.firstOrNull { it.id == id }?.name }
            if (names.size < 2) return
            selection.update { it.copy(remember = RememberState(names, CompoundRules.suggestedName(names))) }
        }

        /** Moves the part at [index] one place up ([delta] = -1) or down (1); the name follows the new order. */
        fun onPartMoved(
            index: Int,
            delta: Int,
        ) = updateRemember { dialog ->
            val target = index + delta
            if (index !in dialog.parts.indices || target !in dialog.parts.indices) return@updateRemember dialog
            val parts = dialog.parts.toMutableList().apply { add(target, removeAt(index)) }
            RememberState(parts, CompoundRules.suggestedName(parts))
        }

        fun onNameChanged(name: String) = updateRemember { it.copy(name = name, problem = null, duplicate = false) }

        fun onRememberDismissed() = selection.update { it.copy(remember = null) }

        /** Saves the name as a compound rule when it is the ticked artists in order; leaves the choice mode. */
        fun onRememberConfirmed() {
            val dialog = selection.value.remember ?: return
            if (dialog.isSaving) return
            val problem = CompoundRules.problemWith(dialog.name, dialog.parts)
            if (problem != null) {
                updateRemember { it.copy(problem = problem) }
                return
            }
            updateRemember { it.copy(isSaving = true) }
            viewModelScope.launch {
                try {
                    if (compounds.remember(dialog.name.trim()) < 0) {
                        updateRemember { it.copy(duplicate = true) }
                    } else {
                        selection.value = Selection()
                    }
                } finally {
                    updateRemember { it.copy(isSaving = false) }
                }
            }
        }

        private fun updateRemember(change: (RememberState) -> RememberState) =
            selection.update { state -> state.remember?.let { state.copy(remember = change(it)) } ?: state }

        private data class Selection(
            val selecting: Boolean = false,
            val ids: List<Long> = emptyList(),
            val remember: RememberState? = null,
        )
    }

/**
 * What the artist card renders.
 *
 * @property artist null while loading and when there is no such artist ([isLoaded] tells them apart).
 */
data class ArtistUiState(
    val artist: Artist? = null,
    val discography: ArtistDiscography = ArtistDiscography(),
    val isLoaded: Boolean = false,
)

/**
 * Artist card (33:714): photo, number of songs, top songs and albums. Play counts and the top songs need
 * statistics (0.6), until then the screen shows an empty state there.
 *
 * Opened by id from the artist list ([ARTIST_ID_ARG]) or by name from the player and the album card
 * ([ARTIST_NAME_ARG]), where only names are at hand.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArtistViewModel
    @Inject
    constructor(
        private val library: LibraryRepository,
        savedState: SavedStateHandle,
    ) : ViewModel() {
        private val artistId: Flow<Long?> =
            savedState.get<Long>(ARTIST_ID_ARG)?.takeIf { it != NO_ARTIST }?.let { flowOf(it) }
                ?: flow { emit(savedState.get<String>(ARTIST_NAME_ARG)?.let { library.artistId(it) }) }

        val state: StateFlow<ArtistUiState> =
            artistId.flatMapLatest { id ->
                if (id == null) {
                    flowOf(ArtistUiState(isLoaded = true))
                } else {
                    combine(
                        library.observeArtists().map { artists -> artists.firstOrNull { it.id == id } },
                        library.observeArtistAlbums(id),
                    ) { artist, discography -> ArtistUiState(artist, discography, isLoaded = true) }
                }
            }.stateIn(this, ArtistUiState())

        /** The user picked a picture ([uri] from the photo picker); it becomes the artist photo. */
        fun onPhotoPicked(uri: String) {
            val id = state.value.artist?.id ?: return
            viewModelScope.launch {
                // A picture that cannot be read leaves the old photo; there is nothing the user can fix.
                runCatching { library.setArtistPhoto(id, uri) }
            }
        }

        fun onPhotoRemoved() {
            val id = state.value.artist?.id ?: return
            viewModelScope.launch { library.removeArtistPhoto(id) }
        }
    }

/**
 * What the album card renders. [album] is null while loading and after the album disappeared.
 *
 * @property artistPhotos photo path of each of `album.artists`, in the same order; null where the artist has no photo.
 */
data class AlbumUiState(
    val album: Album? = null,
    val tracks: List<Track> = emptyList(),
    val currentTrackId: Long? = null,
    val artistPhotos: List<String?> = emptyList(),
)

/** Photo path of each name in [names] taken from the library's [artists] (null when the artist has none). */
internal fun photosOf(
    names: List<String>,
    artists: List<Artist>,
): List<String?> {
    val byName = artists.associate { it.name to it.photoPath }
    return names.map { byName[it] }
}

/** Album card (4:311). */
@HiltViewModel
class AlbumViewModel
    @Inject
    constructor(
        private val library: LibraryRepository,
        private val player: PlayerController,
        savedState: SavedStateHandle,
    ) : ViewModel() {
        private val albumId: Long = checkNotNull(savedState[ALBUM_ID_ARG])

        val state: StateFlow<AlbumUiState> =
            combine(
                library.observeAlbums().map { albums -> albums.firstOrNull { it.id == albumId } },
                library.observeAlbumTracks(albumId),
                player.currentTrackId(),
                library.observeArtists(),
            ) { album, tracks, current, artists ->
                AlbumUiState(album, tracks, current, photosOf(album?.artists.orEmpty(), artists))
            }.stateIn(this, AlbumUiState())

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

        /** Artists of the track for the track menu ("Go to artist"). */
        suspend fun artistChoices(trackId: Long): List<ArtistChoice> = library.artistChoices(trackId)
    }

/**
 * What the list of tracks renders.
 *
 * @property artistName set when the list shows the songs of one artist; the screen uses it as the title.
 */
data class TracksUiState(
    val tracks: List<Track> = emptyList(),
    val currentTrackId: Long? = null,
    val artistName: String? = null,
    val isLoaded: Boolean = false,
)

/**
 * All tracks of the library ("All songs" on the home screen) or of one artist ("All Songs" on the artist card,
 * when [ARTIST_ID_ARG] is set). A screen without a mockup, DESIGN.md, section 7.
 */
@HiltViewModel
class TracksViewModel
    @Inject
    constructor(
        private val library: LibraryRepository,
        private val player: PlayerController,
        savedState: SavedStateHandle,
    ) : ViewModel() {
        private val artistId: Long? = savedState.get<Long>(ARTIST_ID_ARG)?.takeIf { it != NO_ARTIST }

        private val tracks = artistId?.let(library::observeArtistTracks) ?: library.observeTracks()
        private val artistName =
            artistId?.let {
                    id ->
                library.observeArtists().map { artists -> artists.firstOrNull { it.id == id }?.name }
            }
                ?: flowOf(null)

        val state: StateFlow<TracksUiState> =
            combine(tracks, player.currentTrackId(), artistName) { tracks, current, name ->
                TracksUiState(tracks, current, name, isLoaded = true)
            }.stateIn(this, TracksUiState())

        fun onTrackClicked(index: Int) {
            val tracks = state.value.tracks
            if (index in tracks.indices) player.play(tracks, index)
        }

        fun onTrackAction(
            track: Track,
            action: TrackAction,
        ) = player.onTrackAction(track, action)

        /** Artists of the track for the track menu ("Go to artist"). */
        suspend fun artistChoices(trackId: Long): List<ArtistChoice> = library.artistChoices(trackId)
    }

/** Artists of the track as the artist picker shows them. */
internal suspend fun LibraryRepository.artistChoices(trackId: Long): List<ArtistChoice> =
    artistsOfTrack(trackId).map { ArtistChoice(it.id, it.name, it.photoPath) }
