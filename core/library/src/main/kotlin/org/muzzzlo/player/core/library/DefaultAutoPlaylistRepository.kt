package org.muzzzlo.player.core.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import org.muzzzlo.player.core.database.dao.LibraryDao
import org.muzzzlo.player.core.model.AutoPlaylist
import org.muzzzlo.player.core.model.AutoPlaylistRepository
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.Track
import javax.inject.Inject

/** [AutoPlaylistRepository] over the ordered queries of [LibraryDao]. */
internal class DefaultAutoPlaylistRepository
    @Inject
    constructor(
        private val dao: LibraryDao,
        private val dispatchers: DispatcherProvider,
    ) : AutoPlaylistRepository {
        override fun observeTracks(playlist: AutoPlaylist): Flow<List<Track>> {
            val tracks =
                when (playlist) {
                    AutoPlaylist.Favorites -> dao.observeFavoriteTracks()
                    AutoPlaylist.TopSongs -> dao.observeRatedTracks()
                }
            return combine(tracks, dao.observeTrackArtists(), dao.observeAlbums()) { t, artists, albums ->
                toTracks(t, artists.groupBy({ it.trackId }, { it.name }), albums.associate { it.id to it.title })
            }.flowOn(dispatchers.default)
        }
    }
