package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AlbumRatingTest {
    @Test
    fun `an album without ratings has none`() {
        assertNull(AlbumRating.of(emptyList()))
        assertNull(AlbumRating.of(listOf(null, null)))
        assertNull(AlbumRating.of(0, 0))
    }

    @Test
    fun `one rated track is enough`() {
        assertEquals(67, AlbumRating.of(listOf(null, 67, null)))
    }

    @Test
    fun `zero is a rating and pulls the mean down`() {
        assertEquals(0, AlbumRating.of(listOf(0)))
        assertEquals(50, AlbumRating.of(listOf(100, 0)))
        assertEquals(50, AlbumRating.of(listOf(100, 0, null)))
    }

    @Test
    fun `the mean is rounded to a tenth with half going up`() {
        // Mean 10.5, 10.33, 10.67, 0.5, 0.33, 99.67, 67.5, 5.2 in tenths.
        val cases =
            listOf(
                listOf(10, 11) to 11,
                listOf(10, 10, 11) to 10,
                listOf(10, 11, 11) to 11,
                listOf(1, 0, 0, 1) to 1,
                listOf(1, 0, 0) to 0,
                listOf(100, 100, 99) to 100,
                listOf(67, 68) to 68,
                listOf(5, 5, 5, 5, 6) to 5,
            )
        cases.forEach { (ratings, expected) -> assertEquals(expected, AlbumRating.of(ratings), "$ratings") }
    }

    @Test
    fun `the database form gives the same answers as the list form`() {
        val ratings = listOf(67, 68, 0, 100, 33)
        assertEquals(AlbumRating.of(ratings), AlbumRating.of(ratings.sum().toLong(), ratings.size))
    }
}
