package org.muzzzlo.player.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.muzzzlo.player.core.designsystem.component.TrackAction
import org.muzzzlo.player.core.model.Album
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.Track
import javax.inject.Inject

/** What the library screen renders. */
data class LibraryUiState(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    /** Album opened from the albums tab, its tracks are shown instead of the list. */
    val openAlbum: Album? = null,
    val albumTracks: List<Track> = emptyList(),
    val isScanning: Boolean = false,
)

@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        private val library: LibraryRepository,
        private val player: PlayerController,
    ) : ViewModel() {
        private val scanning = MutableStateFlow(false)
        private val opened = MutableStateFlow<Pair<Album, List<Track>>?>(null)

        val state: StateFlow<LibraryUiState> =
            combine(
                library.observeTracks(),
                library.observeAlbums(),
                scanning,
                opened,
            ) { tracks, albums, isScanning, album ->
                LibraryUiState(tracks, albums, album?.first, album?.second.orEmpty(), isScanning)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())

        /** Called once the audio permission is granted, and by the refresh button. */
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

        fun onTrackClicked(
            tracks: List<Track>,
            index: Int,
        ) = player.play(tracks, index)

        /** Item chosen in the track menu. Navigation items are handled by the screen. */
        fun onTrackAction(
            track: Track,
            action: TrackAction,
        ) {
            when (action) {
                TrackAction.PlayNext -> player.playNext(track)
                TrackAction.AddToQueue -> player.addToQueue(track)
                TrackAction.OpenQueue -> Unit
                // Placeholders: playlists, favorites and rating come in 1.1, the tag editor in 0.3.6.
                TrackAction.AddToPlaylist,
                TrackAction.AddToFavorites,
                TrackAction.Rate,
                TrackAction.EditTags,
                -> Unit
            }
        }

        fun onAlbumClicked(album: Album) {
            viewModelScope.launch {
                library.observeAlbumTracks(album.id).collect { opened.value = album to it }
            }
        }

        fun onAlbumClosed() {
            opened.value = null
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
