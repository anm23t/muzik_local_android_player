package org.muzzzlo.player.feature.library

import org.muzzzlo.player.core.model.Album
import org.muzzzlo.player.core.model.NameNormalizer

/**
 * What the album list can be sorted by (DESIGN.md, section 3, "Опции сортировки").
 *
 * @property ascendingByDefault direction used when the field is chosen: names and years go up,
 * counts and dates go down so the biggest and newest come first.
 */
enum class AlbumSortField(
    val ascendingByDefault: Boolean,
) {
    Title(true),
    Artist(true),
    Year(true),
    TrackCount(false),
    DateAdded(false),
}

/** Sort order of the album list: a field and a direction. */
data class AlbumSort(
    val field: AlbumSortField = AlbumSortField.Title,
    val ascending: Boolean = field.ascendingByDefault,
) {
    /** Choosing the selected field again flips the direction, another field starts in its default direction. */
    fun choose(next: AlbumSortField): AlbumSort = if (next == field) copy(ascending = !ascending) else AlbumSort(next)
}

/**
 * Albums in the given order. Albums without the sorted value (no year, no artist, no date) stay at the end in both
 * directions. Equal values are ordered by title and then by id, so the list does not jump between updates.
 */
fun List<Album>.sortedBy(sort: AlbumSort): List<Album> {
    val (known, unknown) = partition { it.hasValueFor(sort.field) }
    val byField = fieldComparator(sort.field).let { if (sort.ascending) it else it.reversed() }
    return known.sortedWith(byField.then(tieBreaker)) + unknown.sortedWith(tieBreaker)
}

private val tieBreaker: Comparator<Album> = compareBy<Album> { NameNormalizer.key(it.title) }.thenBy { it.id }

private fun Album.hasValueFor(field: AlbumSortField): Boolean =
    when (field) {
        AlbumSortField.Title, AlbumSortField.TrackCount -> true
        AlbumSortField.Artist -> artists.isNotEmpty()
        AlbumSortField.Year -> year != null
        AlbumSortField.DateAdded -> dateAdded != null
    }

private fun fieldComparator(field: AlbumSortField): Comparator<Album> =
    when (field) {
        AlbumSortField.Title -> compareBy { NameNormalizer.key(it.title) }
        AlbumSortField.Artist -> compareBy { NameNormalizer.key(it.artistsText) }
        AlbumSortField.Year -> compareBy { it.year }
        AlbumSortField.TrackCount -> compareBy { it.trackCount }
        AlbumSortField.DateAdded -> compareBy { it.dateAdded }
    }
