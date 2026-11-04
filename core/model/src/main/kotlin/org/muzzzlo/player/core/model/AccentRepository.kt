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

/**
 * Colors of the live background (task 2.0.8), 4 muted ARGB colors. Implemented in `core:library`; calls switch to
 * the I/O dispatcher.
 */
interface BackgroundPaletteRepository {
    /** The colors of the cover of [trackId]; the palette of the default red when it has no cover. */
    suspend fun paletteOf(trackId: Long): List<Int>

    /** The palette of the default red, for a switched off dynamic accent. */
    val defaultPalette: List<Int>
}
