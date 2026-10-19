package org.muzzzlo.player.core.model

/**
 * Artist names that contain a comma, such as `Tyler, The Creator` (ARCHITECTURE.md, section 4).
 *
 * The splitter only needs the match keys, so the source (database table or built-in list) stays hidden.
 */
fun interface CompoundNames {
    /**
     * Enabled compound names. Each entry is the list of name fragments in order, every fragment already passed
     * through [NameNormalizer.key]: `Tyler, The Creator` is `["tyler", "the creator"]`.
     */
    fun matchKeys(): Set<List<String>>

    companion object {
        /** No compound names: every comma splits. */
        val None = CompoundNames { emptySet() }
    }
}
