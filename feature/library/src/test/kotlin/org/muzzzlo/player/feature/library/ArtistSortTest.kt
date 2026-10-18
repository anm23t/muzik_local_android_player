package org.muzzzlo.player.feature.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.muzzzlo.player.core.model.Artist

class ArtistSortTest {
    @ParameterizedTest(name = "{0} ascending={1}")
    @MethodSource("cases")
    fun `artists are sorted by the chosen field`(
        field: ArtistSortField,
        ascending: Boolean,
        expectedIds: List<Long>,
    ) {
        assertEquals(expectedIds, library.sortedBy(ArtistSort(field, ascending)).map { it.id })
    }

    @Test
    fun `the default sort is by name from A to Z`() {
        assertEquals(ArtistSort(ArtistSortField.Name, ascending = true), DefaultArtistSort)
    }

    @Test
    fun `counts and dates start from the biggest`() {
        assertEquals(
            ArtistSort(ArtistSortField.TrackCount, false),
            DefaultArtistSort.choose(ArtistSortField.TrackCount),
        )
        assertEquals(ArtistSort(ArtistSortField.DateAdded, false), DefaultArtistSort.choose(ArtistSortField.DateAdded))
        assertEquals(ArtistSort(ArtistSortField.Name, false), DefaultArtistSort.choose(ArtistSortField.Name))
    }

    @Test
    fun `an empty list stays empty`() {
        ArtistSortField.entries.forEach { field ->
            assertEquals(emptyList<Artist>(), emptyList<Artist>().sortedBy(ArtistSort(field)))
        }
    }

    private companion object {
        // id, name, tracks, date added
        val library =
            listOf(
                Artist(1, "Travis Scott", 20, dateAdded = 300),
                Artist(2, "kai angel", 67, dateAdded = null),
                Artist(3, "Хаски", 20, dateAdded = 100),
                Artist(4, "  Kai Angel ", 1, dateAdded = 200),
                Artist(5, "9mice", 5, dateAdded = 400),
            )

        @JvmStatic
        fun cases(): List<Arguments> =
            listOf(
                // Digits before letters, Cyrillic after Latin; the same key is decided by id.
                Arguments.of(ArtistSortField.Name, true, listOf(5L, 2L, 4L, 1L, 3L)),
                Arguments.of(ArtistSortField.Name, false, listOf(3L, 1L, 2L, 4L, 5L)),
                // Equal counts are ordered by name in both directions.
                Arguments.of(ArtistSortField.TrackCount, false, listOf(2L, 1L, 3L, 5L, 4L)),
                Arguments.of(ArtistSortField.TrackCount, true, listOf(4L, 5L, 1L, 3L, 2L)),
                // No date goes last in both directions.
                Arguments.of(ArtistSortField.DateAdded, false, listOf(5L, 1L, 4L, 3L, 2L)),
                Arguments.of(ArtistSortField.DateAdded, true, listOf(3L, 4L, 1L, 5L, 2L)),
            )
    }
}
