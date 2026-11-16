package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeneratedSetsTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 20_000 * day

    private fun fact(
        id: Long,
        rating: Int? = null,
        favorite: Boolean = false,
        lastPlayed: Long? = null,
        added: Long? = null,
    ) = TrackFacts(
        track = Track(id, "content://$id", "Song $id", listOf("A"), null, null, 1000, null, ratingTenths = rating),
        titleKey = "song %03d".format(id),
        isFavorite = favorite,
        favoritedAtMs = if (favorite) 0 else null,
        lastPlayedAtMs = lastPlayed,
        dateAddedMs = added,
    )

    private fun input(
        facts: List<TrackFacts>,
        recentPlays: Map<Long, Int> = emptyMap(),
        epochDay: Long = 20_000,
    ) = GeneratedInput(facts, recentPlays, now, epochDay)

    @Test
    fun `on repeat takes the most played of the last days, ties by the latest listen`() {
        val facts = (1L..30L).map { fact(it, lastPlayed = it) }
        val plays = (1L..30L).associateWith { if (it <= 25) (it % 5).toInt() + 1 else 0 }
        val set = GeneratedSets.build(GeneratedKind.ON_REPEAT, input(facts, plays))

        assertEquals(GeneratedSets.SIZE, set.size)
        assertEquals(listOf(24L, 19L, 14L, 9L, 4L), set.take(5))
        assertTrue(set.none { it > 25 })
    }

    @Test
    fun `forgotten favorites are loved tracks and not played for 60 days or never`() {
        val facts =
            listOf(
                fact(1, favorite = true),
                fact(2, rating = 80, lastPlayed = now - 61 * day),
                fact(3, rating = 80, lastPlayed = now - 59 * day),
                fact(4, rating = 79),
                fact(5, favorite = true, lastPlayed = now - 60 * day - 1),
                fact(6),
            )
        val set = GeneratedSets.build(GeneratedKind.FORGOTTEN_FAVORITES, input(facts))

        assertEquals(setOf(1L, 2L, 5L), set.toSet())
    }

    @Test
    fun `best rated takes random tracks from 8_0 up, at most 20`() {
        val facts = (1L..40L).map { fact(it, rating = if (it % 2 == 0L) 85 else 70) }
        val set = GeneratedSets.build(GeneratedKind.BEST_RATED, input(facts))

        assertEquals(GeneratedSets.SIZE, set.size)
        assertTrue(set.all { it % 2 == 0L })
    }

    @Test
    fun `recently added puts the latest first, unknown dates left out`() {
        val facts = listOf(fact(1, added = 10), fact(2, added = 30), fact(3), fact(4, added = 20))
        assertEquals(listOf(2L, 4L, 1L), GeneratedSets.build(GeneratedKind.RECENTLY_ADDED, input(facts)))
    }

    @Test
    fun `the set does not change during the day and changes on the next day`() {
        val facts = (1L..60L).map { fact(it, rating = 90) }
        val today = GeneratedSets.build(GeneratedKind.BEST_RATED, input(facts, epochDay = 20_000))
        val again = GeneratedSets.build(GeneratedKind.BEST_RATED, input(facts.reversed().reversed(), epochDay = 20_000))
        val tomorrow = GeneratedSets.build(GeneratedKind.BEST_RATED, input(facts, epochDay = 20_001))

        assertEquals(today, again)
        assertNotEquals(today, tomorrow)
    }

    @Test
    fun `kinds of one day are shuffled differently`() {
        val facts = (1L..60L).map { fact(it, rating = 90, favorite = true) }
        val best = GeneratedSets.build(GeneratedKind.BEST_RATED, input(facts))
        val forgotten = GeneratedSets.build(GeneratedKind.FORGOTTEN_FAVORITES, input(facts))
        assertNotEquals(best, forgotten)
    }

    @Test
    fun `a set with fewer than five tracks is not shown`() {
        fun playlist(count: Int) = GeneratedPlaylist(GeneratedKind.ON_REPEAT, 1, 0, count, 0)
        assertFalse(playlist(4).isShown)
        assertTrue(playlist(5).isShown)
    }

    @Test
    fun `an empty library gives empty sets`() {
        GeneratedKind.entries.forEach { assertEquals(emptyList<Long>(), GeneratedSets.build(it, input(emptyList()))) }
    }

    @Test
    fun `gradients are one per ready-made kind, the daily one follows the day`() {
        val kinds = GeneratedKind.entries.map(CoverGradients::forKind)
        assertEquals(kinds.size, kinds.toSet().size)
        assertTrue(kinds.all { it in 0 until CoverGradients.COUNT })
        assertEquals(CoverGradients.forDay(3), CoverGradients.forDay(3 + CoverGradients.COUNT.toLong()))
        assertNotEquals(CoverGradients.forDay(3), CoverGradients.forDay(4))
        assertTrue(CoverGradients.forDay(-1) in 0 until CoverGradients.COUNT)
    }

    @Test
    fun `kinds are found by their stored key`() {
        GeneratedKind.entries.forEach { assertEquals(it, GeneratedKind.fromKey(it.key)) }
        assertNull(GeneratedKind.fromKey("rage"))
    }
}
