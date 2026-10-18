package org.muzzzlo.player

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.designsystem.component.CoverLoader
import org.muzzzlo.player.core.designsystem.component.CoverSource
import org.muzzzlo.player.core.model.CoverRepository
import org.muzzzlo.player.core.model.DispatcherProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gives the screens decoded covers: the file comes from [CoverRepository], the decoded bitmaps are kept in memory
 * in a small LRU so scrolling a list back does not decode them again. Memory entries are keyed by file path, and
 * the path changes when the cover's file changes, so a stale bitmap is never shown.
 */
@Singleton
class AppCoverLoader
    @Inject
    constructor(
        private val covers: CoverRepository,
        private val dispatchers: DispatcherProvider,
    ) : CoverLoader {
        // Sized in kilobytes: an eighth of the Java heap limit would be too greedy for 256 px thumbnails.
        private val memory =
            object : LruCache<String, ImageBitmap>(MEMORY_KB) {
                override fun sizeOf(
                    key: String,
                    value: ImageBitmap,
                ): Int = value.width * value.height * BYTES_PER_PIXEL / BYTES_IN_KB
            }

        override suspend fun load(source: CoverSource): ImageBitmap? {
            val path =
                when (source) {
                    is CoverSource.Track -> covers.trackCover(source.id)
                    is CoverSource.Album -> covers.albumCover(source.id)
                } ?: return null
            return memory.get(path) ?: decode(path)?.also { memory.put(path, it) }
        }

        private suspend fun decode(path: String): ImageBitmap? =
            withContext(dispatchers.io) { BitmapFactory.decodeFile(path)?.asImageBitmap() }

        private companion object {
            /** 16 MB: about 16 covers of 512 px. */
            const val MEMORY_KB = 16 * 1024
            const val BYTES_PER_PIXEL = 4
            const val BYTES_IN_KB = 1024
        }
    }
