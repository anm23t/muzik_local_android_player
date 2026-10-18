package org.muzzzlo.player.core.model

/**
 * The accent color of a track, taken from its cover (DESIGN.md, section 4). Implemented in `core:library`.
 *
 * Calls do file and database I/O and switch to the I/O dispatcher themselves.
 */
interface AccentRepository {
    /**
     * The accent of the track as an opaque ARGB color, already adjusted for contrast. Null when the cover gives
     * none (no cover, grey, near black or white) or the track is gone: the interface then uses the default red.
     * The first call for an album reads its cover; the result is kept in the database until the file changes.
     */
    suspend fun accentOf(trackId: Long): Int?
}
