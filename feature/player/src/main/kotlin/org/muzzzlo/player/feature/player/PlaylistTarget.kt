package org.muzzzlo.player.feature.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.muzzzlo.player.core.designsystem.component.AddToPlaylist
import org.muzzzlo.player.core.designsystem.component.PlaylistChoice
import org.muzzzlo.player.core.model.PlaylistNames
import org.muzzzlo.player.core.model.PlaylistRepository

/** "Add to playlist" of the track menu for the track with [trackId]; writes run in [scope]. */
internal fun PlaylistRepository.addToPlaylist(
    scope: CoroutineScope,
    trackId: Long,
): AddToPlaylist =
    AddToPlaylist(
        load = { observePlaylists().first().map { PlaylistChoice(it.id, it.name) } },
        onAdd = { playlistId -> scope.launch { addTracks(playlistId, listOf(trackId)) } },
        onCreate = { name ->
            PlaylistNames.clean(name)?.let { clean -> scope.launch { createWithTracks(clean, listOf(trackId)) } }
        },
    )
