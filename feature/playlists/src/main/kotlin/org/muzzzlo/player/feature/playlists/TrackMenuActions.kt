package org.muzzzlo.player.feature.playlists

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.muzzzlo.player.core.designsystem.component.AddToPlaylist
import org.muzzzlo.player.core.designsystem.component.ArtistChoice
import org.muzzzlo.player.core.designsystem.component.PlaylistChoice
import org.muzzzlo.player.core.designsystem.component.TrackAction
import org.muzzzlo.player.core.designsystem.component.TrackMarksMenu
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.PlaylistNames
import org.muzzzlo.player.core.model.PlaylistRepository
import org.muzzzlo.player.core.model.Track
import org.muzzzlo.player.core.model.TrackMarksRepository

/** The track menu of a card whose list is not edited by hand: queue actions, artists, playlists, marks. */
internal class TrackMenuActions(
    private val playlists: PlaylistRepository,
    private val library: LibraryRepository,
    private val player: PlayerController,
    private val marks: TrackMarksRepository,
    private val scope: CoroutineScope,
) {
    fun onTrackAction(
        track: Track,
        action: TrackAction,
    ) {
        when (action) {
            TrackAction.PlayNext -> player.playNext(track)
            TrackAction.AddToQueue -> player.addToQueue(track)
            // Navigation, pickers, favorites and rating are handled by the screen and the menu.
            else -> Unit
        }
    }

    /** Artists of the track for the track menu ("Go to artist"). */
    suspend fun artistChoices(trackId: Long): List<ArtistChoice> =
        library.artistsOfTrack(trackId).map { ArtistChoice(it.id, it.name, it.photoPath) }

    /** "Add to playlist" of the track menu. */
    fun addToPlaylist(trackId: Long): AddToPlaylist =
        AddToPlaylist(
            load = { playlists.observePlaylists().first().map { PlaylistChoice(it.id, it.name) } },
            onAdd = { id -> scope.launch { playlists.addTracks(id, listOf(trackId)) } },
            onCreate = { name ->
                PlaylistNames.clean(name)?.let {
                        clean ->
                    scope.launch { playlists.createWithTracks(clean, listOf(trackId)) }
                }
            },
        )

    /** "Add to favorites" and "Rate" of the track menu. */
    fun trackMarks(trackId: Long): TrackMarksMenu = marks.menuFor(scope, trackId)
}
