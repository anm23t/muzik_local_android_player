package org.muzzzlo.player.core.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.database.dao.LibraryDao
import org.muzzzlo.player.core.database.dao.PlaylistDao
import org.muzzzlo.player.core.database.dao.PlaylistRow
import org.muzzzlo.player.core.database.entity.PlaylistEntity
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.Playlist
import org.muzzzlo.player.core.model.PlaylistEntry
import org.muzzzlo.player.core.model.PlaylistNames
import org.muzzzlo.player.core.model.PlaylistOrder
import org.muzzzlo.player.core.model.PlaylistRepository
import java.time.Clock
import javax.inject.Inject

/** [PlaylistRepository] over the `playlist` and `playlist_track` tables. */
internal class DefaultPlaylistRepository
    @Inject
    constructor(
        private val dao: PlaylistDao,
        private val library: LibraryDao,
        private val dispatchers: DispatcherProvider,
        private val clock: Clock,
    ) : PlaylistRepository {
        override fun observePlaylists(): Flow<List<Playlist>> =
            dao.observePlaylists().map { rows -> rows.map { it.toPlaylist() } }.flowOn(dispatchers.default)

        override fun observePlaylist(playlistId: Long): Flow<Playlist?> =
            dao.observePlaylist(playlistId).map { it?.toPlaylist() }.flowOn(dispatchers.default)

        override fun observeEntries(playlistId: Long): Flow<List<PlaylistEntry>> =
            combine(
                dao.observeEntries(playlistId),
                library.observeTrackArtists(),
                library.observeAlbums(),
            ) { entries, credits, albumRows ->
                val artists = credits.groupBy({ it.trackId }, { it.name })
                val albums = albumRows.associate { it.id to it.title }
                entries.map { PlaylistEntry(it.entryId, it.track.toTrack(artists, albums)) }
            }.flowOn(dispatchers.default)

        override suspend fun create(name: String): Long =
            withContext(dispatchers.io) {
                dao.insertPlaylist(PlaylistEntity(name = validName(name), createdAt = clock.millis()))
            }

        override suspend fun rename(
            playlistId: Long,
            name: String,
        ) = withContext(dispatchers.io) { dao.rename(playlistId, validName(name)) }

        override suspend fun delete(playlistId: Long) = withContext(dispatchers.io) { dao.deletePlaylist(playlistId) }

        override suspend fun addTracks(
            playlistId: Long,
            trackIds: List<Long>,
        ) = withContext(dispatchers.io) { dao.append(playlistId, trackIds) }

        override suspend fun removeEntry(entryId: Long) = withContext(dispatchers.io) { dao.deleteEntry(entryId) }

        override suspend fun reorder(
            playlistId: Long,
            entryIds: List<Long>,
        ) = withContext(dispatchers.io) {
            val slots = dao.slots(playlistId).map { PlaylistOrder.Slot(it.id, it.visible) }
            dao.setOrder(PlaylistOrder.reorder(slots, entryIds))
        }

        override suspend fun markPlayed(playlistId: Long) =
            withContext(dispatchers.io) { dao.setLastPlayed(playlistId, clock.millis()) }

        private fun validName(name: String): String =
            requireNotNull(PlaylistNames.clean(name)) { "A playlist name must not be blank" }

        private fun PlaylistRow.toPlaylist() = Playlist(id, name, trackCount, firstTrackId, createdAt, lastPlayedAt)
    }
