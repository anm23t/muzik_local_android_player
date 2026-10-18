package org.muzzzlo.player.feature.library

import org.muzzzlo.player.core.model.Album
import org.muzzzlo.player.core.model.NameNormalizer

/** What the album list can be sorted by (DESIGN.md, section 3, "Опции сортировки"). */
enum class AlbumSortField(
    override val ascendingByDefault: Boolean,
) : SortField {
    Title(true),
    Artist(true),
    Year(true),
    TrackCount(false),
    DateAdded(false),
}

typealias AlbumSort = ListSort<AlbumSortField>

/** The album list starts sorted by title from A to Z. */
val DefaultAlbumSort = AlbumSort(AlbumSortField.Title)

/**
 * Albums in the given order. Albums without the sorted value (no year, no artist, no date) stay at the end in both
 * directions. Equal values are ordered by title and then by id.
 */
fun List<Album>.sortedBy(sort: AlbumSort): List<Album> =
    sortedBy(sort, Album::hasValueFor, ::albumComparator, albumTieBreaker)

private val albumTieBreaker: Comparator<Album> = compareBy<Album> { NameNormalizer.key(it.title) }.thenBy { it.id }

private fun Album.hasValueFor(field: AlbumSortField): Boolean =
    when (field) {
        AlbumSortField.Title, AlbumSortField.TrackCount -> true
        AlbumSortField.Artist -> artists.isNotEmpty()
        AlbumSortField.Year -> year != null
        AlbumSortField.DateAdded -> dateAdded != null
    }

private fun albumComparator(field: AlbumSortField): Comparator<Album> =
    when (field) {
        AlbumSortField.Title -> compareBy { NameNormalizer.key(it.title) }
        AlbumSortField.Artist -> compareBy { NameNormalizer.key(it.artistsText) }
        AlbumSortField.Year -> compareBy { it.year }
        AlbumSortField.TrackCount -> compareBy { it.trackCount }
        AlbumSortField.DateAdded -> compareBy { it.dateAdded }
    }
