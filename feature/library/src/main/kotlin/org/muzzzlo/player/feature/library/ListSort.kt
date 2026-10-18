package org.muzzzlo.player.feature.library

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
 * The sort of a list screen kept in the [SavedStateHandle], so it survives process death. It is not a setting:
 * a new visit of the screen starts with [default].
 *
 * @param key prefix of the saved state keys, unique per screen.
 * @param fields the fields that can be restored; an unknown saved name falls back to [default].
 */
internal class SavedSort<F>(
    private val savedState: SavedStateHandle,
    private val key: String,
    fields: List<F>,
    default: ListSort<F>,
) where F : Enum<F>, F : SortField {
    private val current = MutableStateFlow(restore(fields, default))
    val sort: StateFlow<ListSort<F>> = current.asStateFlow()

    /** A sort option was tapped: the list follows at once, a second tap flips the direction. */
    fun choose(field: F) {
        val next = current.value.choose(field)
        savedState["$key.field"] = next.field.name
        savedState["$key.ascending"] = next.ascending
        current.value = next
    }

    private fun restore(
        fields: List<F>,
        default: ListSort<F>,
    ): ListSort<F> {
        val name = savedState.get<String>("$key.field")
        val field = fields.firstOrNull { it.name == name } ?: return default
        return ListSort(field, savedState.get<Boolean>("$key.ascending") ?: field.ascendingByDefault)
    }
}
