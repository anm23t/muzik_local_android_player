package org.muzzzlo.player.feature.playlists

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.muzzzlo.player.core.model.AutoPlaylist
import org.muzzzlo.player.core.model.AutoPlaylistRepository
import org.muzzzlo.player.core.model.Track

/** Automatic playlists whose tracks a test sets by hand. */
class FakeAutoPlaylists : AutoPlaylistRepository {
    val tracks = MutableStateFlow<Map<AutoPlaylist, List<Track>>>(emptyMap())

    override fun observeTracks(playlist: AutoPlaylist): Flow<List<Track>> = tracks.map { it[playlist].orEmpty() }
}
