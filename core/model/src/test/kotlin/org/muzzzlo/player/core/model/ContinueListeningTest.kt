package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ContinueListeningTest {
    private fun album(id: Long) = Album(id, "A$id", emptyList(), null, 1)

    private val library = (1L..8L).map(::album)

    private fun ids(
        recent: List<Long>,
        queue: Album? = null,
        limit: Int = ContinueListening.MAX_TILES,
    ) = ContinueListening.albums(recent, library, queue, limit).map { it.id }

    @Test
    fun `history order is kept`() {
        assertEquals(listOf(3L, 1L, 2L), ids(listOf(3, 1, 2)))
    }

    @Test
    fun `no more than the limit`() {
        assertEquals(listOf(8L, 7L, 6L, 5L, 4L), ids(listOf(8, 7, 6, 5, 4, 3, 2, 1)))
        assertEquals(listOf(8L, 7L), ids(listOf(8, 7, 6), limit = 2))
    }

    @Test
    fun `an album that is gone is skipped and the next one takes its place`() {
        assertEquals(listOf(2L, 3L), ids(listOf(99, 2, 3)))
    }

    @Test
    fun `repeated ids show once`() {
        assertEquals(listOf(2L, 1L), ids(listOf(2, 2, 1, 2)))
    }

    @Test
    fun `an empty history shows the album of the restored queue`() {
        assertEquals(listOf(4L), ids(emptyList(), queue = album(4)))
    }

    @Test
    fun `a history of vanished albums also falls back to the queue`() {
        assertEquals(listOf(4L), ids(listOf(98, 99), queue = album(4)))
    }

    @Test
    fun `the queue album is not added to a history`() {
        assertEquals(listOf(1L), ids(listOf(1), queue = album(4)))
    }

    @Test
    fun `nothing at all gives an empty row`() {
        assertEquals(emptyList<Long>(), ids(emptyList()))
    }
}
