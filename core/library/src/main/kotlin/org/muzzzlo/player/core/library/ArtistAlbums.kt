package org.muzzzlo.player.core.library

import org.muzzzlo.player.core.database.dao.ArtistAlbumRef
import org.muzzzlo.player.core.model.Album
import org.muzzzlo.player.core.model.ArtistDiscography
import org.muzzzlo.player.core.model.NameNormalizer

/** A release with at most this many tracks on a single disc is a single. */
internal const val SINGLE_MAX_TRACKS = 3

/**
 * Splits the albums of an artist into the artist card sections (ARCHITECTURE.md, section 9):
 * - "Appears on": the artist is not among the album artists, whatever the size;
 * - "Singles": the artist is an album artist, the release has one disc and at most [SINGLE_MAX_TRACKS] tracks by
 *   the "total tracks" tag, or by the number of tracks in the library when no track has that tag;
 * - "Albums": the other albums credited to the artist.
 *
 * Each section goes from the newest year, albums without a year last, then by title. Refs to albums missing from
 * [albums] (no present tracks) are dropped.
 */
internal fun artistDiscography(
    albums: List<Album>,
    refs: List<ArtistAlbumRef>,
): ArtistDiscography {
    val refById = refs.associateBy { it.albumId }
    val sections =
        albums
            .mapNotNull { album -> refById[album.id]?.let { album to sectionOf(album, it) } }
            .sortedWith(compareBy(newestFirst) { it.first })
            .groupBy({ it.second }, { it.first })
    return ArtistDiscography(
        albums = sections[Section.Albums].orEmpty(),
        singles = sections[Section.Singles].orEmpty(),
        appearsOn = sections[Section.AppearsOn].orEmpty(),
    )
}

private enum class Section { Albums, Singles, AppearsOn }

private fun sectionOf(
    album: Album,
    ref: ArtistAlbumRef,
): Section =
    when {
        !ref.isOwn -> Section.AppearsOn
        isSingle(album, ref) -> Section.Singles
        else -> Section.Albums
    }

private fun isSingle(
    album: Album,
    ref: ArtistAlbumRef,
): Boolean {
    val oneDisc = (ref.maxDiscTotal ?: 1) <= 1 && (ref.maxDiscNumber ?: 1) <= 1
    val tracks = ref.maxTrackTotal ?: album.trackCount
    return oneDisc && tracks <= SINGLE_MAX_TRACKS
}

private val newestFirst: Comparator<Album> =
    compareByDescending<Album> { it.year ?: Int.MIN_VALUE }
        .thenBy { NameNormalizer.key(it.title) }
        .thenBy { it.id }
