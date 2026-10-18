package org.muzzzlo.player.core.model

/**
 * Small covers for lists and the player, cut from the files' pictures and kept in a size-limited cache
 * (ARCHITECTURE.md, section 3). Implemented in `core:library`.
 *
 * Calls do file I/O and switch to the I/O dispatcher themselves.
 */
interface CoverRepository {
    /**
     * Path of the cached JPEG cover of the track, made on the first request. Null when the track has no cover or
     * its file cannot be read. The name changes when the file changes, so a path can be remembered by its value.
     */
    suspend fun trackCover(trackId: Long): String?

    /** Path of the cover of an album: the cover of its first track that has one. Null when none has. */
    suspend fun albumCover(albumId: Long): String?
}
