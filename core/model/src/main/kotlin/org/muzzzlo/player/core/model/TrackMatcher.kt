package org.muzzzlo.player.core.model

import kotlin.math.abs

/**
 * Finds a track of the library by what a backup knows about it: title, artists and length (ids and URIs differ
 * between devices). Titles and artists are compared by [NameNormalizer.key]; lengths may differ by
 * [TOLERANCE_MS], because players round them differently.
 */
class TrackMatcher<T>(
    items: List<T>,
    private val title: (T) -> String,
    private val artists: (T) -> List<String>,
    private val durationMs: (T) -> Long,
) {
    private val byTitle = items.groupBy { NameNormalizer.key(title(it)) }

    /** The track with the same title, artist tags and length, or null. The first one wins among equal tracks. */
    fun find(
        title: String,
        artists: List<String>,
        durationMs: Long,
    ): T? {
        val wanted = artists.map(NameNormalizer::key)
        return candidates(title, durationMs).firstOrNull {
                item ->
            this.artists(item).map(NameNormalizer::key) == wanted
        }
    }

    /** The only track with this title and length, or null when there is none or when it is ambiguous. */
    fun findUnique(
        title: String,
        durationMs: Long,
    ): T? = candidates(title, durationMs).singleOrNull()

    private fun candidates(
        title: String,
        durationMs: Long,
    ): List<T> =
        byTitle[NameNormalizer.key(title)].orEmpty().filter { abs(this.durationMs(it) - durationMs) <= TOLERANCE_MS }

    companion object {
        const val TOLERANCE_MS = 1000L
    }
}
