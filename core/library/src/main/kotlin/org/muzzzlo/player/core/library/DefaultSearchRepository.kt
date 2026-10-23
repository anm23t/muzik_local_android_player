package org.muzzzlo.player.core.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import org.muzzzlo.player.core.database.dao.LibraryDao
import org.muzzzlo.player.core.model.ArtistAlias
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.PlaylistRepository
import org.muzzzlo.player.core.model.SearchIndex
import org.muzzzlo.player.core.model.SearchRepository
import javax.inject.Inject

/**
 * [SearchRepository] over the library lists the other screens show (missing files are left out there already) and
 * the old spellings of artists from `artist_alias`. The keys are built off the main thread on every change.
 */
internal class DefaultSearchRepository
    @Inject
    constructor(
        private val library: LibraryRepository,
        private val playlists: PlaylistRepository,
        private val dao: LibraryDao,
        private val dispatchers: DispatcherProvider,
    ) : SearchRepository {
        override fun observeIndex(): Flow<SearchIndex> =
            combine(
                library.observeTracks(),
                library.observeAlbums(),
                library.observeArtists(),
                dao.observeAliases(),
                playlists.observePlaylists(),
            ) { tracks, albums, artists, aliases, lists ->
                SearchIndex(tracks, albums, artists, aliases.map { ArtistAlias(it.artistId, it.aliasDisplay) }, lists)
            }.flowOn(dispatchers.default)
    }
