package org.muzzzlo.player.core.model

import java.text.Normalizer

/** One spelling of an artist in a [VariantGroup]. */
data class ArtistVariant(
    val artistId: Long,
    val name: String,
    val trackCount: Int,
)

/**
 * Artists whose names differ only in case, accents or punctuation ("Beyoncé" and "Beyonce").
 *
 * @property variants ordered with the suggested correct spelling first.
 */
data class VariantGroup(val variants: List<ArtistVariant>) {
    /** The spelling the check suggests: the most frequent one in the library. */
    val suggested: ArtistVariant get() = variants.first()
}

/**
 * The "Check" button: finds artists that are probably one and the same (HANDOFF.md, aliases). Pure logic; the
 * database side is [ArtistCheckRepository].
 */
object ArtistCheck {
    private val marks = Regex("\\p{M}+")

    /** Comparison key stronger than [NameNormalizer.key]: also ignores accents and punctuation. */
    fun looseKey(name: String): String {
        val base = Normalizer.normalize(NameNormalizer.key(name), Normalizer.Form.NFD).replace(marks, "")
        return base.filter(Char::isLetterOrDigit)
    }

    /**
     * Groups [artists] by [looseKey]. Names with no letters or digits ("!!!") are never grouped. In a group the
     * artist with the most tracks comes first, then the longer name (it keeps the punctuation), then the lower id.
     */
    fun groups(artists: List<ArtistVariant>): List<VariantGroup> =
        artists
            .filter { looseKey(it.name).isNotEmpty() }
            .groupBy { looseKey(it.name) }
            .values
            .filter { it.size > 1 }
            .map { group ->
                VariantGroup(
                    group.sortedWith(
                        compareByDescending<ArtistVariant> { it.trackCount }
                            .thenByDescending { it.name.length }
                            .thenBy { it.artistId },
                    ),
                )
            }.sortedBy { looseKey(it.suggested.name) }
}
