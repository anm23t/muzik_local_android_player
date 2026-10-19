package org.muzzzlo.player.core.model

import kotlinx.coroutines.flow.Flow

/**
 * The compound names table (ARCHITECTURE.md, section 4): reading it for the scanner, adding, removing and
 * switching rules off. Every change cuts the raw artist tags of the affected tracks again, without reading
 * files, and removes artists nothing refers to. Implemented in `core:library`.
 */
interface CompoundRepository {
    /** All rules, built-in and the user's, by name. */
    fun observeRules(): Flow<List<CompoundRule>>

    /** Enabled rules as the splitter wants them. */
    suspend fun compoundNames(): CompoundNames

    /** Adds the built-in rules that the table does not know yet. Existing rows and their `enabled` stay. */
    suspend fun syncBuiltIn()

    /**
     * Saves [name] as a rule of the user, for example `Tyler, The Creator`, and cuts the tracks again.
     * Returns the number of tracks whose artists changed, -1 when the name was already a rule.
     */
    suspend fun remember(name: String): Int

    /** Removes a rule of the user, or switches a built-in one off. Returns the number of tracks that changed. */
    suspend fun remove(id: Long): Int

    /**
     * How many tracks would change their artists if the rule [id] were removed or switched off (it is on), or
     * switched on (it is off). Nothing is written; 0 for an unknown id.
     */
    suspend fun countAffectedBy(id: Long): Int

    /** Switches a built-in rule on again. Returns the number of tracks that changed. */
    suspend fun enable(id: Long): Int
}
