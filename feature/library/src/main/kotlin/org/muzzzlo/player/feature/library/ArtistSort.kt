package org.muzzzlo.player.feature.library

import org.muzzzlo.player.core.model.Artist
import org.muzzzlo.player.core.model.NameNormalizer

/**
 * What the artist list can be sorted by (DESIGN.md, section 3, "Опции сортировки"). Sorting by the number of
 * plays needs statistics and is added in 0.6.
 */
enum class ArtistSortField(
    override val ascendingByDefault: Boolean,
) : SortField {
    Name(true),
    TrackCount(false),
    DateAdded(false),
}

typealias ArtistSort = ListSort<ArtistSortField>

/** The artist list starts sorted by name from A to Z. */
val DefaultArtistSort = ArtistSort(ArtistSortField.Name)

/**
 * Artists in the given order. Artists without a date stay at the end in both directions.
 * Equal values are ordered by name and then by id.
 */
fun List<Artist>.sortedBy(sort: ArtistSort): List<Artist> =
    sortedBy(sort, Artist::hasValueFor, ::artistComparator, artistTieBreaker)

private val artistTieBreaker: Comparator<Artist> = compareBy<Artist> { NameNormalizer.key(it.name) }.thenBy { it.id }

private fun Artist.hasValueFor(field: ArtistSortField): Boolean =
    when (field) {
        ArtistSortField.Name, ArtistSortField.TrackCount -> true
        ArtistSortField.DateAdded -> dateAdded != null
    }

private fun artistComparator(field: ArtistSortField): Comparator<Artist> =
    when (field) {
        ArtistSortField.Name -> compareBy { NameNormalizer.key(it.name) }
        ArtistSortField.TrackCount -> compareBy { it.trackCount }
        ArtistSortField.DateAdded -> compareBy { it.dateAdded }
    }
