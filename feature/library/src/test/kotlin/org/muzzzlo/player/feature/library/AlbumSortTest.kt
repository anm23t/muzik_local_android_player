package org.muzzzlo.player.feature.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.muzzzlo.player.core.model.Album

class AlbumSortTest {
    @ParameterizedTest(name = "{0} ascending={1}")
    @MethodSource("cases")
    fun `albums are sorted by the chosen field`(
        field: AlbumSortField,
        ascending: Boolean,
        expectedIds: List<Long>,
    ) {
        assertEquals(expectedIds, library.sortedBy(AlbumSort(field, ascending)).map { it.id })
    }

    @Test
    fun `the default sort is by title from A to Z`() {
        assertEquals(AlbumSort(AlbumSortField.Title, ascending = true), AlbumSort())
    }

    @Test
    fun `choosing the selected field flips the direction`() {
        val sort = AlbumSort(AlbumSortField.Year, ascending = true)

        assertEquals(AlbumSort(AlbumSortField.Year, ascending = false), sort.choose(AlbumSortField.Year))
        assertEquals(sort, sort.choose(AlbumSortField.Year).choose(AlbumSortField.Year))
    }

    @Test
    fun `choosing another field starts in its default direction`() {
        val sort = AlbumSort(AlbumSortField.Title, ascending = false)

        assertEquals(AlbumSort(AlbumSortField.Artist, true), sort.choose(AlbumSortField.Artist))
        assertEquals(AlbumSort(AlbumSortField.TrackCount, false), sort.choose(AlbumSortField.TrackCount))
        assertEquals(AlbumSort(AlbumSortField.DateAdded, false), sort.choose(AlbumSortField.DateAdded))
        assertEquals(AlbumSort(AlbumSortField.Year, true), sort.choose(AlbumSortField.Year))
    }

    @Test
    fun `an empty list stays empty`() {
        AlbumSortField.entries.forEach { field ->
            assertEquals(emptyList<Album>(), emptyList<Album>().sortedBy(AlbumSort(field)))
        }
    }

    private companion object {
        // id, title, artists, year, tracks, date added
        val library =
            listOf(
                album(1, "beta", listOf("Zed"), 2010, 10, 300),
                album(2, "Alpha", listOf("anna"), null, 3, null),
                album(3, "Ålbum", emptyList(), 1999, 10, 100),
                album(4, "  ALPHA ", listOf("Bob", "Anna"), 2010, 12, 200),
                album(5, "Гроза", listOf("Хаски"), 2017, 1, 400),
            )

        @JvmStatic
        fun cases(): List<Arguments> =
            listOf(
                // Same title key: the id decides. Cyrillic goes after Latin, "Å" after "z".
                Arguments.of(AlbumSortField.Title, true, listOf(2L, 4L, 1L, 3L, 5L)),
                Arguments.of(AlbumSortField.Title, false, listOf(5L, 3L, 1L, 2L, 4L)),
                // No artist goes last in both directions; case does not matter ("anna" < "bob, anna").
                Arguments.of(AlbumSortField.Artist, true, listOf(2L, 4L, 1L, 5L, 3L)),
                Arguments.of(AlbumSortField.Artist, false, listOf(5L, 1L, 4L, 2L, 3L)),
                // Equal years are ordered by title; no year goes last.
                Arguments.of(AlbumSortField.Year, true, listOf(3L, 4L, 1L, 5L, 2L)),
                Arguments.of(AlbumSortField.Year, false, listOf(5L, 4L, 1L, 3L, 2L)),
                // Equal counts are ordered by title, not reversed with the direction.
                Arguments.of(AlbumSortField.TrackCount, false, listOf(4L, 1L, 3L, 2L, 5L)),
                Arguments.of(AlbumSortField.TrackCount, true, listOf(5L, 2L, 1L, 3L, 4L)),
                // No date goes last.
                Arguments.of(AlbumSortField.DateAdded, false, listOf(5L, 1L, 4L, 3L, 2L)),
                Arguments.of(AlbumSortField.DateAdded, true, listOf(3L, 4L, 1L, 5L, 2L)),
            )

        fun album(
            id: Long,
            title: String,
            artists: List<String>,
            year: Int?,
            tracks: Int,
            dateAdded: Long?,
        ) = Album(id, title, artists, year, tracks, dateAdded)
    }
}
