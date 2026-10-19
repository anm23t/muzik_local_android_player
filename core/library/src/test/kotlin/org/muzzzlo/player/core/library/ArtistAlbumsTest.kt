package org.muzzzlo.player.core.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.muzzzlo.player.core.database.dao.ArtistAlbumRef
import org.muzzzlo.player.core.model.Album
import org.muzzzlo.player.core.model.ArtistDiscography

class ArtistAlbumsTest {
    private fun album(
        id: Long,
        title: String = "Album $id",
        year: Int? = 2020,
        trackCount: Int = 10,
    ) = Album(id, title, listOf("A"), year, trackCount)

    /** The section of one credited album: "albums" or "singles". */
    private fun sectionOf(
        album: Album,
        ref: ArtistAlbumRef,
    ): String {
        val discography = artistDiscography(listOf(album), listOf(ref))
        return when {
            discography.singles.isNotEmpty() -> "singles"
            discography.albums.isNotEmpty() -> "albums"
            else -> "appears on"
        }
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        nullValues = ["-"],
        value = [
            // case, tracks in library, max track total, max disc total, max disc number, expected
            "three tracks by tag, 3, 3, -, -, singles",
            "four tracks by tag, 4, 4, -, -, albums",
            "one track by tag, 1, 1, 1, 1, singles",
            "tag wins over a smaller library, 1, 12, -, -, albums",
            "tag wins over a larger library, 5, 3, -, -, singles",
            "no tag and three in library, 3, -, -, -, singles",
            "no tag and four in library, 4, -, -, -, albums",
            "no tag and one in library, 1, -, -, -, singles",
            "two discs by tag, 2, 1, 2, -, albums",
            "second disc without disc total, 2, 1, -, 2, albums",
            "disc one of one, 2, 2, 1, 1, singles",
        ],
    )
    fun `a credited album is a single when it has one disc and at most three tracks`(
        case: String,
        tracksInLibrary: Int,
        trackTotal: Int?,
        discTotal: Int?,
        discNumber: Int?,
        expected: String,
    ) {
        val ref = ArtistAlbumRef(1, isOwn = true, trackTotal, discTotal, discNumber)

        assertEquals(expected, sectionOf(album(1, trackCount = tracksInLibrary), ref), case)
    }

    @Test
    fun `an album credited to someone else appears on whatever its size`() {
        val single = ArtistAlbumRef(1, isOwn = false, maxTrackTotal = 1)
        val long = ArtistAlbumRef(2, isOwn = false, maxTrackTotal = 20)

        val discography = artistDiscography(listOf(album(1, trackCount = 1), album(2)), listOf(single, long))

        assertEquals(ArtistDiscography(appearsOn = listOf(album(1, trackCount = 1), album(2))), discography)
    }

    @Test
    fun `every album lands in exactly one section`() {
        val albums = (1L..6L).map { album(it, trackCount = it.toInt()) }
        val refs = albums.map { ArtistAlbumRef(it.id, isOwn = it.id % 2 == 0L) }

        val discography = artistDiscography(albums, refs)
        val ids = (discography.albums + discography.singles + discography.appearsOn).map { it.id }

        assertEquals(albums.map { it.id }.sorted(), ids.sorted())
        assertEquals(listOf(2L), discography.singles.map { it.id })
        assertEquals(listOf(4L, 6L), discography.albums.map { it.id })
        assertEquals(listOf(1L, 3L, 5L), discography.appearsOn.map { it.id })
    }

    @Test
    fun `each section goes from the newest year, without a year last, then by title`() {
        val albums =
            listOf(
                album(1, "Old", 2010),
                album(2, "No year", null),
                album(3, "New", 2024),
                album(4, "beta", 2015),
                album(5, "Alpha", 2015),
                album(6, "alpha", 2015),
            )
        val refs = albums.map { ArtistAlbumRef(it.id, isOwn = true) }

        assertEquals(listOf(3L, 5L, 6L, 4L, 1L, 2L), artistDiscography(albums, refs).albums.map { it.id })
    }

    @Test
    fun `refs to albums without present tracks are dropped and empty sections stay empty`() {
        assertTrue(artistDiscography(listOf(album(1)), listOf(ArtistAlbumRef(2, isOwn = true))).isEmpty)
        assertTrue(artistDiscography(emptyList(), emptyList()).isEmpty)

        val onlySingle = artistDiscography(listOf(album(1, trackCount = 1)), listOf(ArtistAlbumRef(1, isOwn = true)))
        assertEquals(ArtistDiscography(singles = listOf(album(1, trackCount = 1))), onlySingle)
    }
}
