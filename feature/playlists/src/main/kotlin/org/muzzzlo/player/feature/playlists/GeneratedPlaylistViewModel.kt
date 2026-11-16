package org.muzzzlo.player.feature.playlists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.muzzzlo.player.core.designsystem.component.AddToPlaylist
import org.muzzzlo.player.core.designsystem.component.ArtistChoice
import org.muzzzlo.player.core.designsystem.component.PlaylistChoice
import org.muzzzlo.player.core.designsystem.component.TrackAction
import org.muzzzlo.player.core.designsystem.component.TrackMarksMenu
import org.muzzzlo.player.core.model.GeneratedKind
import org.muzzzlo.player.core.model.GeneratedPlaylist
import org.muzzzlo.player.core.model.GeneratedPlaylistRepository
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.PlaylistNames
import org.muzzzlo.player.core.model.PlaylistRepository
import org.muzzzlo.player.core.model.Track
import org.muzzzlo.player.core.model.TrackMarksRepository
import javax.inject.Inject

/** Navigation argument of the card of a generated playlist: [GeneratedKind.key]. */
const val GENERATED_KIND_ARG = "generatedKind"

/**
 * What the card of a generated playlist renders (task 2.5.7).
 *
 * @property kind null for an unknown key in the route.
 * @property playlist today's set; null while it is not built or not shown.
 * @property savedAs the name the playlist got from "Add", shown once; null otherwise.
 */
data class GeneratedPlaylistUiState(
    val kind: GeneratedKind? = null,
    val playlist: GeneratedPlaylist? = null,
    val tracks: List<Track> = emptyList(),
    val currentTrackId: Long? = null,
    val isLoaded: Boolean = false,
    val savedAs: String? = null,
)

/**
 * The card of a generated playlist: play (the listens remember the kind, for "Continue listening" and the daily mix),
 * the track menu and "Add", which keeps today's set as a playlist of the user. Nothing to edit.
 */
@HiltViewModel
class GeneratedPlaylistViewModel
    @Inject
    constructor(
        private val generated: GeneratedPlaylistRepository,
        private val playlists: PlaylistRepository,
        private val library: LibraryRepository,
        private val player: PlayerController,
        private val marks: TrackMarksRepository,
        savedState: SavedStateHandle,
    ) : ViewModel() {
        private val kind: GeneratedKind? = GeneratedKind.fromKey(savedState[GENERATED_KIND_ARG])
        private val savedAs = MutableStateFlow<String?>(null)

        val state: StateFlow<GeneratedPlaylistUiState> =
            combine(
                kind?.let(generated::observePlaylist) ?: flowOf(null),
                kind?.let(generated::observeTracks) ?: flowOf(emptyList()),
                player.state.map { it.currentTrack?.id }.distinctUntilChanged(),
                savedAs,
            ) { playlist, tracks, current, saved ->
                GeneratedPlaylistUiState(kind, playlist, tracks, current, isLoaded = true, savedAs = saved)
            }.stateIn(this, GeneratedPlaylistUiState(kind))

        fun onPlay() = onTrackClicked(0)

        fun onTrackClicked(index: Int) {
            val tracks = state.value.tracks
            if (index in tracks.indices) player.play(tracks, index, generatedKind = kind?.key)
        }

        /** "Add": [name] is the localized name of the kind; a taken name gets a number. */
        fun onAdd(name: String) {
            val kind = kind ?: return
            viewModelScope.launch { savedAs.value = generated.saveSnapshot(kind, name).name }
        }

        fun onSavedMessageDismissed() = savedAs.update { null }

        fun onTrackAction(
            track: Track,
            action: TrackAction,
        ) {
            when (action) {
                TrackAction.PlayNext -> player.playNext(track)
                TrackAction.AddToQueue -> player.addToQueue(track)
                // Navigation, pickers, favorites and rating are handled by the screen and the menu.
                TrackAction.GoToAlbum,
                TrackAction.GoToArtist,
                TrackAction.AddToPlaylist,
                TrackAction.RemoveFromPlaylist,
                TrackAction.AddToFavorites,
                TrackAction.Rate,
                TrackAction.EditTags,
                TrackAction.Share,
                TrackAction.SleepTimer,
                TrackAction.DeleteFile,
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
