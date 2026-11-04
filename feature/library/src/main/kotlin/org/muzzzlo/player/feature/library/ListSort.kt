package org.muzzzlo.player.feature.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.muzzzlo.player.core.model.SettingsRepository
import org.muzzzlo.player.core.model.SortedList
import org.muzzzlo.player.core.model.StoredSort

/**
 * A field a list can be sorted by.
 *
 * @property ascendingByDefault direction used when the field is chosen: names and years go up,
 * counts and dates go down so the biggest and newest come first.
 */
interface SortField {
    val ascendingByDefault: Boolean
}

/** Sort order of a list (albums, artists): a field and a direction. */
data class ListSort<F>(
    val field: F,
    val ascending: Boolean = field.ascendingByDefault,
) where F : Enum<F>, F : SortField {
    /** Choosing the selected field again flips the direction, another field starts in its default direction. */
    fun choose(next: F): ListSort<F> = if (next == field) copy(ascending = !ascending) else ListSort(next)
}

/**
 * Items in the given order. Items without the sorted value ([hasValue] is false) stay at the end in both
 * directions. Equal values are ordered by [tieBreaker], so the list does not jump between updates.
 */
internal fun <T, F> List<T>.sortedBy(
    sort: ListSort<F>,
    hasValue: T.(F) -> Boolean,
    comparator: (F) -> Comparator<T>,
    tieBreaker: Comparator<T>,
): List<T> where F : Enum<F>, F : SortField {
    val (known, unknown) = partition { it.hasValue(sort.field) }
    val byField = comparator(sort.field).let { if (sort.ascending) it else it.reversed() }
    return known.sortedWith(byField.then(tieBreaker)) + unknown.sortedWith(tieBreaker)
}

/**
 * The sort of a list screen as the user last chose it. It is kept in the app settings (and so in the backup), so a
 * new visit of the screen, also after a restart, starts with the last choice; before any choice it is [default].
 *
 * @param list the settings entry of this screen.
 * @param fields the fields that can be restored; a saved name that no field has any more gives [default].
 */
internal class SavedSort<F>(
    private val settings: SettingsRepository,
    private val list: SortedList,
    private val fields: List<F>,
    private val default: ListSort<F>,
) where F : Enum<F>, F : SortField {
    /** The current sort. It emits once the settings are read, so a list is never drawn in the wrong order first. */
    val sort: Flow<ListSort<F>> = settings.settings.map { restore(it.listSorts[list]) }.distinctUntilChanged()

    /** A sort option was tapped: the list follows at once, a second tap flips the direction. */
    suspend fun choose(field: F) {
        val next = sort.first().choose(field)
        settings.setListSort(list, StoredSort(next.field.name, next.ascending))
    }

    private fun restore(stored: StoredSort?): ListSort<F> {
        val field = fields.firstOrNull { it.name == stored?.field } ?: return default
        return ListSort(field, stored?.ascending ?: field.ascendingByDefault)
    }
}
