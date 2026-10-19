package org.muzzzlo.player.core.model

/**
 * A rule of the compound names table: an artist name with a comma inside.
 *
 * @property builtinId id in the built-in list, null for a rule the user made.
 * @property enabled a built-in rule the user switched off stays in the table, disabled.
 */
data class CompoundRule(
    val id: Long,
    val displayName: String,
    val builtinId: String?,
    val enabled: Boolean,
)

/** Why a name typed in the "Remember as one artist" dialog cannot be saved. */
enum class CompoundNameProblem {
    /** The name has fewer than two parts: without a comma there is nothing to glue. */
    TooShort,

    /** The parts of the name are not the selected artists in this order. */
    NotSelectedParts,
}

/** Match keys and checks for compound names (ARCHITECTURE.md, section 4). Pure logic. */
object CompoundRules {
    private const val KEY_SEPARATOR = "|"

    /** Comma-separated parts of [name], each passed through [NameNormalizer.key]; empty parts are dropped. */
    fun fragments(name: String): List<String> =
        name.split(',').map { NameNormalizer.key(it) }.filter { it.isNotEmpty() }

    /** Value of `artist_compound.match_key` for [name]: `Tyler, The Creator` gives `tyler|the creator`. */
    fun matchKey(name: String): String = fragments(name).joinToString(KEY_SEPARATOR)

    /** The inverse of [matchKey]: the fragments a stored key stands for. */
    fun fragmentsOfKey(matchKey: String): List<String> = matchKey.split(KEY_SEPARATOR).filter { it.isNotEmpty() }

    /** The [CompoundNames] the splitter needs for the stored [matchKeys] (enabled rules only). */
    fun compoundNames(matchKeys: Collection<String>): CompoundNames {
        val keys = matchKeys.map(::fragmentsOfKey).filter { it.size >= 2 }.toSet()
        return CompoundNames { keys }
    }

    /**
     * Checks the [name] typed for the artists [selected], in the order they were put: the name must be exactly
     * these artists as comma-separated parts, compared by [NameNormalizer.key] (so a changed spelling of a part,
     * like case, is fine). Returns null when the name is fine.
     */
    fun problemWith(
        name: String,
        selected: List<String>,
    ): CompoundNameProblem? {
        val parts = fragments(name)
        return when {
            parts.size < 2 -> CompoundNameProblem.TooShort
            parts != selected.map { NameNormalizer.key(it) } -> CompoundNameProblem.NotSelectedParts
            else -> null
        }
    }

    /** The name the dialog starts with: the selected artists in order, as the tag would write them. */
    fun suggestedName(selected: List<String>): String = selected.joinToString(", ") { it.trim() }
}
