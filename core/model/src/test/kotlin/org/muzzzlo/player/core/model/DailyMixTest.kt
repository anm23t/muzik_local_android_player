package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class DailyMixTest {
    private val day = 24L * 60 * 60 * 1000
    private val today = 20_000L
    private val now = today * day + 12 * 60 * 60 * 1000

    private var nextId = 1L

    private fun fact(
        vararg artists: String,
        genres: List<String> = emptyList(),
        lastPlayed: Long? = null,
        favorite: Boolean = false,
        rating: Int? = null,
    ): TrackFacts {
        val id = nextId++
        return TrackFacts(
            track =
                Track(id, "content://$id", "Song $id", artists.toList(), null, null, 1000, null, ratingTenths = rating),
            titleKey = "song $id",
            isFavorite = favorite,
            favoritedAtMs = if (favorite) 0 else null,
            lastPlayedAtMs = lastPlayed,
            genres = genres,
        )
    }

    private fun listen(
        fact: TrackFacts,
        daysAgo: Long,
        fromGenerated: Boolean = false,
    ) = MixListen(
        trackId = fact.track.id,
        artists = fact.track.artists,
        startedAtMs = now - daysAgo * day,
        epochDay = today - daysAgo,
        fromGenerated = fromGenerated,
    )

    private fun mix(
        facts: List<TrackFacts>,
        listens: List<MixListen>,
        yesterday: Set<Long> = emptySet(),
        seed: Int = 1,
    ) = DailyMix.build(facts, listens, yesterday, now, Random(seed))

    private fun artistsOf(
        ids: List<Long>,
        facts: List<TrackFacts>,
    ): List<String> {
        val byId = facts.associateBy { it.track.id }
        return ids.flatMap { byId.getValue(it).track.artists }
    }

    @Test
    fun `no listens at all means no mix`() {
        assertEquals(emptyList<Long>(), mix(List(30) { fact("A") }, emptyList()))
    }

    @Test
    fun `one artist gives at most three of their tracks`() {
        val tracks = List(10) { fact("A") }
        val result = mix(tracks, listOf(listen(tracks[0], daysAgo = 5)))
        assertEquals(3, result.size)
    }

    @Test
    fun `a small library gives what it has`() {
        val tracks = listOf(fact("A"), fact("B"), fact("C"), fact("D", favorite = true))
        val listens = listOf(listen(tracks[0], 5), listen(tracks[1], 5), listen(tracks[2], 5))
        val result = mix(tracks, listens)
        assertEquals(tracks.map { it.track.id }.toSet(), result.toSet())
    }

    @Test
    fun `a full library gives twenty tracks without repeats`() {
        val artists = ('A'..'J').map { it.toString() }
        val tracks = artists.flatMap { artist -> List(6) { fact(artist) } }
        val listens = artists.map { artist -> listen(tracks.first { artist in it.track.artists }, 4) }
        val result = mix(tracks, listens)
        assertEquals(DailyMix.SIZE, result.size)
        assertEquals(result.size, result.toSet().size)
    }

    @Test
    fun `no artist more than three times, a shared track counts for each artist`() {
        val tracks = List(8) { fact("A") } + List(8) { fact("A", "B") } + List(8) { fact("B") } + List(8) { fact("C") }
        val listens = listOf(listen(tracks[0], 3), listen(tracks[20], 3), listen(tracks[30], 3))
        val counts = artistsOf(mix(tracks, listens), tracks).groupingBy { it }.eachCount()
        assertTrue(counts.values.all { it <= DailyMix.MAX_PER_ARTIST }, "$counts")
    }

    @Test
    fun `tracks played in the last two days are left out`() {
        val recent = fact("A", lastPlayed = now - day)
        val old = List(5) { fact("A", lastPlayed = now - 10 * day) }
        val result = mix(listOf(recent) + old, listOf(listen(recent, 1)))
        assertTrue(recent.track.id !in result)
        assertEquals(3, result.size)
    }

    @Test
    fun `yesterday's mix is left out while there is something else`() {
        val tracks = List(6) { fact("A") } + List(6) { fact("B") }
        val listens = listOf(listen(tracks[0], 5), listen(tracks[6], 5))
        val yesterday = (tracks.take(3) + tracks.drop(6).take(3)).map { it.track.id }.toSet()
        val result = mix(tracks, listens, yesterday)
        assertEquals(6, result.size)
        assertTrue(result.none { it in yesterday })
    }

    @Test
    fun `yesterday's tracks come back when nothing else is left`() {
        val tracks = List(3) { fact("A") }
        val yesterday = tracks.map { it.track.id }.toSet()
        assertEquals(yesterday, mix(tracks, listOf(listen(tracks[0], 5)), yesterday).toSet())
    }

    @Test
    fun `without listens in two weeks the whole history is the seed`() {
        val tracks = List(5) { fact("Old") } + List(5) { fact("Other") }
        val result = mix(tracks, listOf(listen(tracks[0], daysAgo = 200)))
        assertEquals(3, result.size)
        assertEquals(setOf("Old"), artistsOf(result, tracks).toSet())
    }

    @Test
    fun `genres of the seed bring other artists`() {
        val seed = fact("A", genres = listOf("Jazz"))
        val sameGenre = List(4) { fact("B", genres = listOf("jazz")) }
        val other = List(4) { fact("C", genres = listOf("Metal")) }
        val result = mix(listOf(seed) + sameGenre + other, listOf(listen(seed, 3)))
        assertTrue("B" in artistsOf(result, sameGenre + seed + other))
        assertTrue("C" !in artistsOf(result, sameGenre + seed + other))
    }

    @Test
    fun `neighbours are artists heard on the same days as the seed`() {
        val seed = fact("A")
        val neighbour = fact("N")
        val stranger = fact("S")
        val more = List(4) { fact("N") } + List(4) { fact("S") }
        val listens =
            listOf(
                listen(seed, 3),
                listen(seed, 40),
                listen(neighbour, 40),
                listen(stranger, 50),
            )
        val all = listOf(seed, neighbour, stranger) + more
        val result = artistsOf(mix(all, listens), all)
        assertTrue("N" in result)
        assertTrue("S" !in result)
    }

    @Test
    fun `forgotten favorites join the mix`() {
        val seed = fact("A")
        val forgotten = fact("Z", favorite = true, lastPlayed = now - 90 * day)
        val loved = fact("Y", rating = 85)
        val result = mix(listOf(seed, forgotten, loved, fact("X")), listOf(listen(seed, 3)))
        assertTrue(forgotten.track.id in result)
        assertTrue(loved.track.id in result)
    }

    @Test
    fun `a listen weighs half after a week and a tenth from a generated playlist`() {
        val track = fact("A")
        val fresh = DailyMix.weightOf(listen(track, 0), now)
        val week = DailyMix.weightOf(listen(track, 7), now)
        val generated = DailyMix.weightOf(listen(track, 0, fromGenerated = true), now)
        assertEquals(1.0, fresh, 1e-9)
        assertEquals(0.5, week, 1e-9)
        assertEquals(DailyMix.GENERATED_WEIGHT, generated, 1e-9)
    }

    @Test
    fun `listens from generated playlists move the seed much less`() {
        val fromSets = (1..10).map { "A$it" }
        val plain = (1..10).map { "B$it" }
        val tracks = (fromSets + plain).flatMap { artist -> List(3) { fact(artist) } }

        fun first(artist: String) = tracks.first { artist in it.track.artists }
        val listens = fromSets.map { listen(first(it), 3, fromGenerated = true) } + plain.map { listen(first(it), 3) }
        repeat(5) { seed ->
            val artists = artistsOf(mix(tracks, listens, seed = seed), tracks)
            assertTrue(artists.count { it.startsWith("B") } > 2 * artists.count { it.startsWith("A") }, "$artists")
        }
    }

    @Test
    fun `the mix is the same all day and another the next day`() {
        val artists = ('A'..'H').map { it.toString() }
        val tracks = artists.flatMap { artist -> List(5) { fact(artist) } }
        val listens = artists.map { artist -> listen(tracks.first { artist in it.track.artists }, 4) }

        fun build(epochDay: Long) =
            GeneratedSets.build(GeneratedKind.DAILY_MIX, GeneratedInput(tracks, emptyMap(), now, epochDay, listens))
        assertEquals(build(today), build(today))
        assertNotEquals(build(today), build(today + 1))
    }
}
