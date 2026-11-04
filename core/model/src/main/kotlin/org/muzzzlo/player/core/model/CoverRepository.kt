package org.muzzzlo.player.core.model

/**
 * Small covers for lists and the player, cut from the files' pictures and kept in a size-limited cache
 * (ARCHITECTURE.md, section 3). Implemented in `core:library`.
 *
 * Calls do file I/O and switch to the I/O dispatcher themselves.
 */
interface CoverRepository {
    /**
     * Path of the cached JPEG cover of the track, made on the first request; a track without a cover of its own
     * gets the folder cover of its album. Null when there is no cover or the file cannot be read. The name changes
     * when the file changes, so a path can be remembered by its value.
     */
    suspend fun trackCover(trackId: Long): String?

    /**
     * Path of the cover of an album: the cover of its first track that has one, otherwise the cover picture of its
     * folder when the user turned folder covers on (task 2.0.5). Null when there is neither.
     */
    suspend fun albumCover(albumId: Long): String?
}
