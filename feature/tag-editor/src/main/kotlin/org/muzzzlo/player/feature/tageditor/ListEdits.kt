package org.muzzzlo.player.feature.tageditor

import org.muzzzlo.player.core.model.ArtistSplitter

/** Separator of the parts of a name glued by [mergeWithNext]; the same comma the splitter cuts by. */
private const val MERGE_SEPARATOR = ", "

/** The artist names [value] splits into by the usual rules (`;`, else `,`); one name when it cannot be split. */
internal fun splitParts(value: String): List<String> = ArtistSplitter.split(listOf(value))

/** True when [splitAt] would change the list: the value holds `;` or `,` between two names. */
internal fun canSplit(value: String): Boolean = splitParts(value).size > 1

/** Replaces the value at [index] with the names it splits into; the list stays as it is when it cannot be split. */
internal fun List<String>.splitAt(index: Int): List<String> {
    val parts = getOrNull(index)?.let(::splitParts).orEmpty()
    if (parts.size < 2) return this
    return take(index) + parts + drop(index + 1)
}

/** Joins the value at [index] with the next one into one name `A, B`; a blank side is dropped. */
internal fun List<String>.mergeWithNext(index: Int): List<String> {
    if (index !in 0 until lastIndex) return this
    val merged = listOfNotNull(this[index].cleaned(), this[index + 1].cleaned()).joinToString(MERGE_SEPARATOR)
    return take(index) + merged + drop(index + 2)
}

/** Swaps the value at [index] with the one above it; the list stays as it is for the first row or a bad index. */
internal fun List<String>.moveUp(index: Int): List<String> = swapped(index - 1, index)

/** Swaps the value at [index] with the one below it; the list stays as it is for the last row or a bad index. */
internal fun List<String>.moveDown(index: Int): List<String> = swapped(index, index + 1)

/** Moves the value at [index] up for a negative [delta] and down for a positive one. */
internal fun List<String>.moved(
    index: Int,
    delta: Int,
): List<String> = if (delta < 0) moveUp(index) else moveDown(index)

private fun List<String>.swapped(
    first: Int,
    second: Int,
): List<String> {
    if (first < 0 || second > lastIndex || first >= second) return this
    return toMutableList().also {
        it[first] = this[second]
        it[second] = this[first]
    }
}
