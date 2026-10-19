package org.muzzzlo.player.core.library

import org.muzzzlo.player.core.model.ArtistSplitter
import org.muzzzlo.player.core.model.CompoundNames

/** A track whose artists come out differently under the new compound names. */
internal data class RecutTrack(
    val trackId: Long,
    val artists: List<String>,
)

/**
 * Cuts the raw artist tags again after the compound names changed (ARCHITECTURE.md, section 4), from the stored
 * values only, without reading files.
 */
internal object CompoundRecalculation {
    /**
     * @param rawValues track id to the raw values of its artist tag.
     * @return the tracks whose split under [after] differs from the split under [before], with the new artists.
     * Only a single value with a comma and no `;` can change, the others are skipped without splitting.
     */
    fun changedTracks(
        rawValues: Map<Long, List<String>>,
        before: CompoundNames,
        after: CompoundNames,
    ): List<RecutTrack> =
        rawValues.mapNotNull { (id, values) ->
            val single = values.singleOrNull()
            if (single == null || ',' !in single || ';' in single) return@mapNotNull null
            val old = ArtistSplitter.split(values, before)
            val new = ArtistSplitter.split(values, after)
            if (old == new) null else RecutTrack(id, new)
        }
}
