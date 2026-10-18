package org.muzzzlo.player.core.library

import org.muzzzlo.player.core.database.dao.LibraryDao
import org.muzzzlo.player.core.model.EmbeddedPicture
import org.muzzzlo.player.core.model.TagReadResult
import javax.inject.Inject

/** The files behind library tracks, as [DefaultTrackTagRepository] needs them; replaced by a fake in tests. */
internal interface TrackFiles {
    /** The MediaStore row of the track's file, null when the track or the file is gone. */
    suspend fun find(trackId: Long): ScannedFile?

    suspend fun readTags(file: ScannedFile): TagReadResult

    suspend fun readCover(file: ScannedFile): EmbeddedPicture?

    /** Reads [file] into the library again. */
    suspend fun reindex(file: ScannedFile)
}

/** [TrackFiles] on MediaStore and the library database. Calls must run off the main thread. */
internal class MediaStoreTrackFiles
    @Inject
    constructor(
        private val dao: LibraryDao,
        private val source: MediaStoreSource,
        private val tagLoader: FileTagLoader,
        private val indexer: LibraryIndexer,
    ) : TrackFiles {
        // The file is looked up again every time: its name, size and stamps may have changed since the last scan.
        override suspend fun find(trackId: Long): ScannedFile? =
            dao.tracksByIds(listOf(trackId)).singleOrNull()?.let { source.file(it.uri) }

        override suspend fun readTags(file: ScannedFile): TagReadResult = tagLoader.load(file.uri, file.displayName)

        override suspend fun readCover(file: ScannedFile): EmbeddedPicture? =
            tagLoader.loadCover(file.uri, file.displayName)

        override suspend fun reindex(file: ScannedFile) = indexer.reindex(file)
    }
