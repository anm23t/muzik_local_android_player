package org.muzzzlo.player.core.library

import androidx.room.withTransaction
import org.muzzzlo.player.core.database.MuzzzloDatabase
import org.muzzzlo.player.core.database.dao.LibraryDao
import org.muzzzlo.player.core.database.dao.TrackStamp
import org.muzzzlo.player.core.database.entity.AlbumArtistEntity
import org.muzzzlo.player.core.database.entity.AlbumEntity
import org.muzzzlo.player.core.database.entity.TrackArtistEntity
import org.muzzzlo.player.core.database.entity.TrackScanFields
import org.muzzzlo.player.core.model.NameNormalizer
import javax.inject.Inject

/** Reads the tags of new or changed files and writes them into the database; other files are not touched. */
class LibraryIndexer
    @Inject
    constructor(
        private val database: MuzzzloDatabase,
        private val dao: LibraryDao,
        private val tagLoader: FileTagLoader,
    ) {
        suspend fun index(files: List<ScannedFile>) {
            val known = dao.trackStamps().associateBy { it.uri }
            val seenUris = files.mapTo(HashSet()) { it.uri }
            val toRead = files.filter { needsReading(known[it.uri], it) }

            toRead.chunked(BATCH_SIZE).forEach { batch ->
                // Tags are read before the transaction, so the database is not locked while files are read.
                val read = batch.map { file -> file to trackMetadata(file, tagLoader.load(file.uri, file.displayName)) }
                database.withTransaction {
                    for ((file, metadata) in read) write(file, metadata, known[file.uri]?.id)
                }
            }

            val gone = known.values.filter { it.uri !in seenUris && !it.isMissing }.map { it.id }
            database.withTransaction {
                if (gone.isNotEmpty()) dao.setMissing(gone, true)
                dao.deleteEmptyAlbums()
            }
        }

        /**
         * New, changed and reappeared files are read. Damaged files are retried on every scan: while quarantined
         * they cost one lookup, and once the user releases them they are read again.
         */
        private fun needsReading(
            stamp: TrackStamp?,
            file: ScannedFile,
        ): Boolean = stamp == null || stamp.dateModified != file.dateModified || stamp.isMissing || stamp.isDamaged

        private suspend fun write(
            file: ScannedFile,
            metadata: TrackMetadata,
            existingId: Long?,
        ) {
            val artists = metadata.artistTagValues
            val artistIds = artists.map { dao.artistId(it, NameNormalizer.key(it)) }
            val albumId = metadata.album?.let { findOrCreateAlbum(it, metadata.year, artistIds) }

            val fields =
                TrackScanFields(
                    id = existingId ?: 0,
                    mediaStoreId = file.mediaStoreId,
                    uri = file.uri,
                    relativePath = file.relativePath,
                    title = metadata.title,
                    titleKey = NameNormalizer.key(metadata.title),
                    artistTagValues = jsonArray(artists),
                    albumId = albumId,
                    trackNumber = metadata.trackNumber,
                    discNumber = metadata.discNumber,
                    year = metadata.year,
                    durationMs = metadata.durationMs,
                    format = metadata.format,
                    bitrate = metadata.bitrateKbps,
                    fileSize = file.fileSize,
                    dateAdded = file.dateAdded,
                    dateModified = file.dateModified,
                    hasCover = metadata.hasCover,
                    hasLyrics = metadata.hasLyrics,
                    isDamaged = metadata.isDamaged,
                )
            val trackId =
                if (existingId == null) {
                    dao.insertTrack(fields.toNewEntity())
                } else {
                    dao.updateScannedTrack(fields)
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
