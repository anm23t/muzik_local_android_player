package org.muzzzlo.player.core.library

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.database.dao.LibraryDao
import org.muzzzlo.player.core.database.entity.TrackEntity
import org.muzzzlo.player.core.model.CoverRepository
import org.muzzzlo.player.core.model.DispatcherProvider
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [CoverRepository] over a [CoverDiskCache] in the app's cache folder, limited to [CACHE_LIMIT_BYTES].
 *
 * The cache key comes from the track row alone ([coverKey]), so a hit costs one database lookup and no file access.
 * An album without a cover in its tags may use the picture of its folder instead ([folderCoverOf]).
 * A miss reads the picture from the file and cuts the thumbnail; misses are made one at a time, which keeps memory
 * low while a list of new covers is scrolled and makes two requests for the same cover read the file once.
 */
@Singleton
internal class DefaultCoverRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dao: LibraryDao,
        private val files: TrackFiles,
        private val thumbnailer: CoverThumbnailer,
        private val dispatchers: DispatcherProvider,
    ) : CoverRepository {
        private val cache = CoverDiskCache(File(context.cacheDir, FOLDER), CACHE_LIMIT_BYTES)
        private val missLock = Mutex()

        override suspend fun trackCover(trackId: Long): String? =
            withContext(dispatchers.io) {
                val track = dao.tracksByIds(listOf(trackId)).singleOrNull() ?: return@withContext null
                // A track without a picture of its own shows the folder cover of its album, if that has one.
                if (track.hasCover) coverOf(track) else track.albumId?.let { folderCoverOf(it) }
            }

        override suspend fun albumCover(albumId: Long): String? =
            withContext(dispatchers.io) {
                val track = dao.firstCoverTrackOfAlbum(albumId)
                if (track != null) coverOf(track) else folderCoverOf(albumId)
            }

        private suspend fun coverOf(track: TrackEntity): String? {
            if (!track.hasCover) return null
            return cached(coverKey(track.uri, track.dateModified, track.fileSize)) {
                files.find(track.id)?.let { files.readCover(it) }?.data
            }
        }

        /**
         * The picture lying next to the tracks of the album (task 2.0.5), set by [FolderCoverIndexer]. Its key comes
         * from the picture's URI and stamp, so a replaced picture gets a new thumbnail.
         */
        private suspend fun folderCoverOf(albumId: Long): String? {
            val cover = dao.albumFolderCover(albumId) ?: return null
            val uri = cover.uri ?: return null
            return cached(coverKey(uri, cover.modified, null)) {
                runCatching { context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() } }
                    .getOrNull()
            }
        }

        /** The thumbnail under [key], cut from the bytes of [picture] on a miss. */
        private suspend fun cached(
            key: String,
            picture: suspend () -> ByteArray?,
        ): String? {
            cache.get(key)?.let { return it.absolutePath }
            return missLock.withLock {
                // Another request may have made it while this one waited.
                cache.get(key)?.let { return@withLock it.absolutePath }
                val data = picture() ?: return@withLock null
                val thumbnail = runCatching { thumbnailer.thumbnail(data) }.getOrNull() ?: return@withLock null
                cache.put(key, thumbnail).absolutePath
            }
        }

        private companion object {
            const val FOLDER = "covers"

            /** 64 MB: about 1500 covers of 512 px. */
            const val CACHE_LIMIT_BYTES = 64L * 1024 * 1024
        }
    }
