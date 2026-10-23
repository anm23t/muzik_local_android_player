package org.muzzzlo.player.core.playback

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.Track
import org.muzzzlo.player.core.model.WaveformPeaks
import org.muzzzlo.player.core.model.WaveformRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [WaveformRepository] over [WaveformDecoder] and [WaveformDiskCache]. One file is decoded at a time ([lock]), so a
 * quick skip through the queue does not start several decoders; a cancelled caller leaves the lock at once. The
 * cache key is the URI with the size and modification time from MediaStore, so an edited file is decoded again.
 */
@UnstableApi
@Singleton
internal class DefaultWaveformRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val decoder: WaveformDecoder,
        private val dispatchers: DispatcherProvider,
    ) : WaveformRepository {
        private val cache = WaveformDiskCache(File(context.cacheDir, FOLDER))
        private val lock = Mutex()

        override suspend fun peaks(track: Track): WaveformPeaks? =
            withContext(dispatchers.io) {
                val key = keyOf(track.uri) ?: return@withContext null
                cache.get(key)
                    ?: lock.withLock {
                        // Another caller may have decoded it while this one waited.
                        cache.get(key) ?: decoder.decode(track.uri)?.also { cache.put(key, it) }
                    }
            }

        private fun keyOf(uri: String): String? {
            val projection = arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED)
            val row =
                runCatching {
                    context.contentResolver.query(Uri.parse(uri), projection, null, null, null)?.use {
                        if (it.moveToFirst()) it.getLong(0) to it.getLong(1) else null
                    }
                }.getOrNull() ?: return null
            return WaveformDiskCache.keyOf(uri, row.first, row.second)
        }

        private companion object {
            const val FOLDER = "waveforms"
        }
    }
