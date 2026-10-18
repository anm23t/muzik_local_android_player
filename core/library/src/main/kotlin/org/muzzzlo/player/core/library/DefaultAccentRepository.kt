package org.muzzzlo.player.core.library

import android.graphics.BitmapFactory
import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.database.dao.LibraryDao
import org.muzzzlo.player.core.model.AccentRepository
import org.muzzzlo.player.core.model.CoverRepository
import org.muzzzlo.player.core.model.DispatcherProvider
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AccentRepository] that picks the color with [AccentPicker] from the small cover of [CoverRepository].
 * The color is cached per album in `album.accent_color` (`NULL` not computed, `0` none, see DATABASE.md);
 * [LibraryIndexer] resets it when a file is read again. A track without an album has no row to keep it in,
 * so its color lives in memory, keyed by the cover file path, which changes together with the file.
 */
@Singleton
internal class DefaultAccentRepository
    @Inject
    constructor(
        private val dao: LibraryDao,
        private val covers: CoverRepository,
        private val dispatchers: DispatcherProvider,
    ) : AccentRepository {
        private val loneTrackColors = ConcurrentHashMap<String, Int>()

        override suspend fun accentOf(trackId: Long): Int? =
            withContext(dispatchers.io) {
                val track = dao.tracksByIds(listOf(trackId)).singleOrNull()
                val color = track?.let { colorOf(trackId, it.albumId) }
                color?.takeIf { it != AccentPicker.NONE }
            }

        private suspend fun colorOf(
            trackId: Long,
            albumId: Long?,
        ): Int {
            if (albumId == null) return pickedFrom(covers.trackCover(trackId), loneTrackColors)
            dao.albumAccent(albumId)?.let { return it }
            return pickedFrom(covers.albumCover(albumId), null).also { dao.setAlbumAccent(albumId, it) }
        }

        private fun pickedFrom(
            coverPath: String?,
            memory: MutableMap<String, Int>?,
        ): Int {
            if (coverPath == null) return AccentPicker.NONE
            return memory?.getOrPut(coverPath) { pick(coverPath) } ?: pick(coverPath)
        }

        private fun pick(coverPath: String): Int = AccentPicker.pick(decodePixels(coverPath))

        /** The cover decoded at about [SAMPLE_SIDE] px: enough for the dominant color, cheap to quantize. */
        private fun decodePixels(path: String): IntArray {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight, SAMPLE_SIDE)
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = BitmapFactory.decodeFile(path, options) ?: return IntArray(0)
            return IntArray(bitmap.width * bitmap.height).also {
                bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                bitmap.recycle()
            }
        }

        private companion object {
            const val SAMPLE_SIDE = 128
        }
    }
