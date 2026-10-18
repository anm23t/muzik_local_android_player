package org.muzzzlo.player.core.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.database.dao.LibraryDao
import org.muzzzlo.player.core.database.entity.TrackEntity
import org.muzzzlo.player.core.model.Album
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.Track
import javax.inject.Inject

internal class DefaultLibraryRepository
    @Inject
    constructor(
        private val dao: LibraryDao,
        private val source: MediaStoreSource,
        private val indexer: LibraryIndexer,
        private val dispatchers: DispatcherProvider,
    ) : LibraryRepository {
        override fun observeTracks(): Flow<List<Track>> =
            combine(dao.observeTracks(), dao.observeTrackArtists(), dao.observeAlbums()) { tracks, artists, albums ->
                toTracks(tracks, artists.groupBy({ it.trackId }, { it.name }), albums.associate { it.id to it.title })
            }.flowOn(dispatchers.default)

        override fun observeAlbumTracks(albumId: Long): Flow<List<Track>> =
            combine(dao.observeAlbumTracks(albumId), dao.observeTrackArtists(), dao.observeAlbums()) { t, a, al ->
                toTracks(t, a.groupBy({ it.trackId }, { it.name }), al.associate { it.id to it.title })
            }.flowOn(dispatchers.default)

        override fun observeAlbums(): Flow<List<Album>> =
            combine(dao.observeAlbums(), dao.observeAlbumArtists()) { albums, artists ->
                val byAlbum = artists.groupBy({ it.albumId }, { it.name })
                albums.map { Album(it.id, it.title, byAlbum[it.id].orEmpty(), it.year, it.trackCount) }
            }.flowOn(dispatchers.default)

        override suspend fun tracksByIds(ids: List<Long>): List<Track> =
            withContext(dispatchers.io) {
                val chunks = ids.distinct().chunked(SQL_VARIABLE_CHUNK)
                val entities = chunks.flatMap { dao.tracksByIds(it) }.associateBy { it.id }
                val artists = chunks.flatMap { dao.artistNamesFor(it) }.groupBy({ it.trackId }, { it.name })
                val albumIds = entities.values.mapNotNull { it.albumId }.distinct()
                val albums =
                    albumIds.chunked(
                        SQL_VARIABLE_CHUNK,
                    ).flatMap { dao.albumTitles(it) }.associate { it.id to it.title }
                toTracks(ids.mapNotNull(entities::get), artists, albums)
            }

        override suspend fun scan() =
            withContext(dispatchers.io) {
                indexer.index(source.query())
            }

        private fun toTracks(
            tracks: List<TrackEntity>,
            artists: Map<Long, List<String>>,
            albumTitles: Map<Long, String>,
        ): List<Track> =
            tracks.map {
                Track(
                    id = it.id,
                    uri = it.uri,
                    title = it.title,
                    artists = artists[it.id].orEmpty(),
                    albumId = it.albumId,
                    albumTitle = it.albumId?.let(albumTitles::get),
                    durationMs = it.durationMs,
                    trackNumber = it.trackNumber,
                )
            }

        private companion object {
            /** SQLite allows 999 variables in a query. */
            const val SQL_VARIABLE_CHUNK = 500
        }
    }
