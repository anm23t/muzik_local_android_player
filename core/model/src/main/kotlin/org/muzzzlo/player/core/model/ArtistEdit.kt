package org.muzzzlo.player.core.model

/**
 * One change of an artist profile, applied to the artist names of every track that credits the artist.
 *
 * The same change covers all three profile actions: a rename has one name in [replacement], a merge has the name
 * of the other artist, a split has several names (for example `Kanye West & JAY-Z` becomes `Kanye West`, `JAY-Z`).
 *
 * @property target the artist being changed, found by [NameNormalizer.key].
 * @property replacement the names that take the place of [target], in order; trimmed, without empty parts and repeats.
 */
class ArtistEdit(
    val target: String,
    replacement: List<String>,
) {
    val replacement: List<String> =
        replacement.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy(NameNormalizer::key)

    init {
        require(this.replacement.isNotEmpty()) { "An artist edit needs at least one name" }
    }

    /**
     * Puts [replacement] where [target] stands, so the order of the other artists stays. A name that is already
     * in the list is not repeated: the first place wins. Lists without [target] come back unchanged.
     */
    fun applyTo(names: List<String>): List<String> {
        val targetKey = NameNormalizer.key(target)
        if (names.none { NameNormalizer.key(it) == targetKey }) return names
        return names
            .flatMap { if (NameNormalizer.key(it) == targetKey) replacement else listOf(it) }
            .distinctBy(NameNormalizer::key)
    }

    /** True when the list holds [target] and the edit would change it. */
    fun changes(names: List<String>): Boolean = applyTo(names) != names

    companion object {
        fun rename(
            target: String,
            newName: String,
        ) = ArtistEdit(target, listOf(newName))

        fun merge(
            target: String,
            into: String,
        ) = ArtistEdit(target, listOf(into))

        fun split(
            target: String,
            into: List<String>,
        ) = ArtistEdit(target, into)
    }
}
