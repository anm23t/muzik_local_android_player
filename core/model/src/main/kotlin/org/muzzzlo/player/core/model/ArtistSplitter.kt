package org.muzzzlo.player.core.model

/**
 * Turns the raw artist values of a file into separate artist names (ARCHITECTURE.md, section 4).
 *
 * Rules: several values in the file are taken as they are; one value with `;` is cut by `;` only; any other
 * single value is cut by `,` and fragments that form a known compound name are glued back together.
 * No other separators are used.
 */
object ArtistSplitter {
    /**
     * @param values values of the artist field in file order.
     * @param compounds names with a comma inside, used only for the comma rule.
     * @return artist names in order, trimmed, without empty parts and without repeats (compared by [NameNormalizer.key]).
     */
    fun split(
        values: List<String>,
        compounds: CompoundNames = CompoundNames.None,
    ): List<String> {
        val cleaned = values.map { it.trim() }.filter { it.isNotEmpty() }
        val names =
            when {
                cleaned.size != 1 -> cleaned
                ';' in cleaned[0] -> cleaned[0].split(';')
                else -> splitByComma(cleaned[0], compounds.matchKeys())
            }
        return names.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { NameNormalizer.key(it) }
    }

    private fun splitByComma(
        value: String,
        compounds: Set<List<String>>,
    ): List<String> {
        val fragments = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val keys = fragments.map { NameNormalizer.key(it) }
        val longest = compounds.maxOfOrNull { it.size } ?: 1
        val result = mutableListOf<String>()
        var index = 0
        while (index < fragments.size) {
            val length = matchLength(keys, index, compounds, longest)
            result += fragments.subList(index, index + length).joinToString(", ")
            index += length
        }
        return result
    }

    /** Number of fragments, starting at [from], that form one name: the longest compound match, else 1. */
    private fun matchLength(
        keys: List<String>,
        from: Int,
        compounds: Set<List<String>>,
        longest: Int,
    ): Int {
        val most = minOf(longest, keys.size - from)
        for (length in most downTo 2) {
            if (keys.subList(from, from + length) in compounds) return length
        }
        return 1
    }
}
