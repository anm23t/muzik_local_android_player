package org.muzzzlo.player.core.library

import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.model.BackgroundPaletteRepository
import org.muzzzlo.player.core.model.CoverRepository
import org.muzzzlo.player.core.model.DispatcherProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BackgroundPaletteRepository] over the small covers of [CoverRepository]. The palette is cheap to compute from a
 * cached thumbnail and needed once per track change, so it lives only in memory, keyed by the cover path, which
 * changes together with the file; no database column (task 2.0.8).
 */
@Singleton
internal class DefaultBackgroundPaletteRepository
    @Inject
    constructor(
        private val covers: CoverRepository,
        private val dispatchers: DispatcherProvider,
    ) : BackgroundPaletteRepository {
        private val cache =
            object : LinkedHashMap<String, List<Int>>(CACHE_SIZE, LOAD_FACTOR, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Int>>?) = size > CACHE_SIZE
            }

        override val defaultPalette: List<Int> by lazy { BackgroundPalette.fromSeed(BackgroundPalette.DEFAULT_RED) }

        override suspend fun paletteOf(trackId: Long): List<Int> =
            withContext(dispatchers.io) {
                val path = covers.trackCover(trackId) ?: return@withContext defaultPalette
                synchronized(cache) { cache[path] }
                    ?: BackgroundPalette.pick(decodeCoverPixels(path)).also { synchronized(cache) { cache[path] = it } }
            }

        private companion object {
            const val CACHE_SIZE = 64
            const val LOAD_FACTOR = 0.75f
        }
    }
