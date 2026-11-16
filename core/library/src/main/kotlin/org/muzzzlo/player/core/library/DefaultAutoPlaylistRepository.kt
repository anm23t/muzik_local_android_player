package org.muzzzlo.player.core.library

import kotlinx.coroutines.flow.Flow
import org.muzzzlo.player.core.model.AutoPlaylist
import org.muzzzlo.player.core.model.AutoPlaylistRepository
import org.muzzzlo.player.core.model.SmartPlaylistRepository
import org.muzzzlo.player.core.model.Track
import javax.inject.Inject

/** [AutoPlaylistRepository] over the built-in rules of smart playlists ([AutoPlaylist.rules], task 2.5.6). */
internal class DefaultAutoPlaylistRepository
    @Inject
    constructor(
        private val smartPlaylists: SmartPlaylistRepository,
    ) : AutoPlaylistRepository {
        override fun observeTracks(playlist: AutoPlaylist): Flow<List<Track>> =
            smartPlaylists.observeTracks(playlist.rules)
    }
