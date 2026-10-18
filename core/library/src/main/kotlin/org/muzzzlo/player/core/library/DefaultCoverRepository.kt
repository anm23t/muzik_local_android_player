package org.muzzzlo.player.core.library

import android.content.Context
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
 * A miss reads the picture from the file and cuts the thumbnail; misses are made one at a time, which keeps memory
 * low while a list of new covers is scrolled and makes two requests for the same cover read the file once.
 */
@Singleton
internal class DefaultCoverRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val dao: LibraryDao,
        private val files: TrackFiles,
        private val thumbnailer: CoverThumbnailer,
        private val dispatchers: DispatcherProvider,
    ) : CoverRepository {
        private val cache = CoverDiskCache(File(context.cacheDir, FOLDER), CACHE_LIMIT_BYTES)
        private val missLock = Mutex()

        override suspend fun trackCover(trackId: Long): String? =
            withContext(dispatchers.io) {
                dao.tracksByIds(listOf(trackId)).singleOrNull()?.let { coverOf(it) }
            }

        override suspend fun albumCover(albumId: Long): String? =
            withContext(dispatchers.io) {
                dao.firstCoverTrackOfAlbum(albumId)?.let { coverOf(it) }
            }

        private suspend fun coverOf(track: TrackEntity): String? {
            if (!track.hasCover) return null
            val key = coverKey(track.uri, track.dateModified, track.fileSize)
            cache.get(key)?.let { return it.absolutePath }
            return missLock.withLock {
                // Another request may have made it while this one waited.
                cache.get(key)?.let { return@withLock it.absolutePath }
                val file = files.find(track.id) ?: return@withLock null
                val picture = files.readCover(file) ?: return@withLock null
                val thumbnail = runCatching { thumbnailer.thumbnail(picture.data) }.getOrNull() ?: return@withLock null
                cache.put(key, thumbnail).absolutePath
            }
        }

        private companion object {
            const val FOLDER = "covers"

            /** 64 MB: about 1500 covers of 512 px. */
            const val CACHE_LIMIT_BYTES = 64L * 1024 * 1024
        }
    }
