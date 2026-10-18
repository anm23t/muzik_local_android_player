package org.muzzzlo.player.core.library

import androidx.room.withTransaction
import org.muzzzlo.player.core.database.MuzzzloDatabase
import org.muzzzlo.player.core.database.dao.LibraryDao
import org.muzzzlo.player.core.database.entity.AlbumArtistEntity
import org.muzzzlo.player.core.database.entity.AlbumEntity
import org.muzzzlo.player.core.database.entity.TrackArtistEntity
import org.muzzzlo.player.core.database.entity.TrackEntity
import org.muzzzlo.player.core.model.NameNormalizer
import javax.inject.Inject

/** Writes scanned files into the database; only new or changed files are touched. */
class LibraryIndexer
    @Inject
    constructor(
        private val database: MuzzzloDatabase,
        private val dao: LibraryDao,
    ) {
        suspend fun index(files: List<ScannedFile>) {
            val known = dao.trackStamps().associateBy { it.uri }
            val seenUris = files.mapTo(HashSet()) { it.uri }

            files.chunked(BATCH_SIZE).forEach { batch ->
                database.withTransaction {
                    for (file in batch) {
                        val stamp = known[file.uri]
                        if (stamp == null || stamp.dateModified != file.dateModified || stamp.isMissing) {
                            write(file, stamp?.id)
                        }
                    }
                }
            }

            val gone = known.values.filter { it.uri !in seenUris && !it.isMissing }.map { it.id }
            database.withTransaction {
                if (gone.isNotEmpty()) dao.setMissing(gone, true)
                dao.deleteEmptyAlbums()
            }
        }

        private suspend fun write(
            file: ScannedFile,
            existingId: Long?,
        ) {
            val artists = artistNames(file.artist)
            val artistIds = artists.map { dao.artistId(it, NameNormalizer.key(it)) }
            val albumId = cleanAlbum(file.album)?.let { findOrCreateAlbum(it, file.year, artistIds) }

            val entity =
                TrackEntity(
                    id = existingId ?: 0,
                    mediaStoreId = file.mediaStoreId,
                    uri = file.uri,
                    relativePath = file.relativePath,
                    title = file.title,
                    titleKey = NameNormalizer.key(file.title),
                    artistTagValues = jsonArray(listOfNotNull(file.artist)),
                    albumId = albumId,
                    trackNumber = file.trackNumber,
                    discNumber = file.discNumber,
                    year = file.year,
                    durationMs = file.durationMs,
                    format = file.mimeType?.substringAfter('/')?.removePrefix("x-"),
                    fileSize = file.fileSize,
                    dateAdded = file.dateAdded,
                    dateModified = file.dateModified,
                )
            val trackId =
                if (existingId == null) {
                    dao.insertTrack(entity)
                } else {
                    dao.updateTrack(entity)
                    dao.clearTrackArtists(existingId)
                    existingId
                }
            dao.insertTrackArtists(artistIds.mapIndexed { i, id -> TrackArtistEntity(trackId, id, i) })
        }

        private suspend fun findOrCreateAlbum(
            title: String,
            year: Int?,
            artistIds: List<Long>,
        ): Long {
            val key = NameNormalizer.key(title)
            dao.albumIdsByKey(key, year).firstOrNull { dao.albumArtistIds(it) == artistIds }?.let { return it }
            val id = dao.insertAlbum(AlbumEntity(title = title, titleKey = key, year = year))
            dao.insertAlbumArtists(artistIds.mapIndexed { i, artistId -> AlbumArtistEntity(id, artistId, i) })
            return id
        }

        private companion object {
            const val BATCH_SIZE = 200
        }
    }
