package org.muzzzlo.player.core.model

import kotlinx.coroutines.flow.Flow

/** Read access to the indexed library plus the rescan entry point. */
interface LibraryRepository {
    fun observeTracks(): Flow<List<Track>>

    fun observeAlbums(): Flow<List<Album>>

    fun observeAlbumTracks(albumId: Long): Flow<List<Track>>

    /** Tracks with the given ids in the same order; ids of missing or unknown tracks are skipped. */
    suspend fun tracksByIds(ids: List<Long>): List<Track>

    /** Scans the device audio and updates the cache. Only new or changed files are read. */
    suspend fun scan()
}
