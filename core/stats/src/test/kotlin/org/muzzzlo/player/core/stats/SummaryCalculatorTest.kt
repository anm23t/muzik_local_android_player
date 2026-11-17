package org.muzzzlo.player.core.stats

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.model.ArtistImage
import org.muzzzlo.player.core.model.CalendarPeriod
import org.muzzzlo.player.core.model.PeriodSummary
import org.muzzzlo.player.core.model.SummaryAlbum
import java.time.YearMonth

class SummaryCalculatorTest {
    private val period = CalendarPeriod.Month(YearMonth.of(2026, 11))

    private val bjork = ArtistLink(1, "Björk", photoPath = null)
    private val thom = ArtistLink(2, "Thom Yorke", photoPath = "/photos/thom.jpg")
    private val burial = ArtistLink(3, "Burial", photoPath = null)

    private fun listen(
        trackId: Long?,
        title: String = "Song $trackId",
        snapshot: List<String> = emptyList(),
        album: Long? = null,
        playedMs: Long = 60_000,
    ) = SummaryListen(trackId, title, snapshot, album, album?.let { "Album $it" }, playedMs)

    private fun calculate(
        listens: List<SummaryListen>,
        artists: Map<Long, List<ArtistLink>> = emptyMap(),
        genres: Map<Long, List<String>> = emptyMap(),
    ): PeriodSummary? = SummaryCalculator.calculate(period, isFinished = true, listens, artists, genres)

    @Test
    fun `an empty period has no summary`() {
        assertNull(calculate(emptyList()))
    }

    @Test
    fun `totals and tops count every counted listen`() {
        val listens = listOf(listen(1, album = 10), listen(1, album = 10), listen(2, album = 20, playedMs = 30_000))
        val summary = calculate(listens, mapOf(1L to listOf(bjork), 2L to listOf(burial)))!!

        assertEquals(3, summary.plays)
        assertEquals(150_000, summary.playedMs)
        assertEquals(listOf(1L, 2L), summary.tracks.map { it.trackId })
        assertEquals(listOf(2, 1), summary.tracks.map { it.plays })
        assertEquals(
            listOf(SummaryAlbum(10, "Album 10", 2, 120_000), SummaryAlbum(20, "Album 20", 1, 30_000)),
            summary.albums,
        )
        assertEquals(1L, summary.topTrack?.trackId)
        assertEquals("Björk", summary.topArtist?.name)
    }

    @Test
    fun `few listens give as many places as there are`() {
        val summary = calculate(listOf(listen(1)), mapOf(1L to listOf(bjork)))!!
        assertEquals(1, summary.tracks.size)
        assertEquals(emptyList<SummaryAlbum>(), summary.albums)
        assertEquals(1, summary.artists.size)
    }

    @Test
    fun `a top keeps five places`() {
        val summary = calculate((1L..8L).map { listen(it, album = it) })!!
        assertEquals(PeriodSummary.TOP, summary.tracks.size)
        assertEquals(PeriodSummary.TOP, summary.albums.size)
    }

    @Test
    fun `ties go by played time, then by name, then by the first listen`() {
        val listens =
            listOf(
                listen(1, title = "beta"),
                listen(2, title = "Alpha"),
                listen(3, title = "gamma", playedMs = 90_000),
                listen(4, title = "alpha"),
            )
        val summary = calculate(listens)!!
        assertEquals(listOf(3L, 2L, 4L, 1L), summary.tracks.map { it.trackId })
    }

    @Test
    fun `a shared track counts for each of its artists once`() {
        val listens = listOf(listen(1), listen(1), listen(2))
        val artists = mapOf(1L to listOf(bjork, thom), 2L to listOf(thom, thom))
        val summary = calculate(listens, artists)!!
        assertEquals(listOf("Thom Yorke" to 3, "Björk" to 2), summary.artists.map { it.name to it.plays })
        assertEquals(listOf("Björk", "Thom Yorke"), summary.tracks.first().artists)
    }

    @Test
    fun `a deleted track counts by its snapshot and joins a known artist`() {
        val listens =
            listOf(
                listen(null, title = "Gone", snapshot = listOf("BJÖRK", "Ghost")),
                listen(null, title = "Gone", snapshot = listOf("BJÖRK", "Ghost")),
                listen(1),
            )
        val summary = calculate(listens, mapOf(1L to listOf(bjork)))!!

        val gone = summary.tracks.first()
        assertNull(gone.trackId)
        assertEquals("Gone", gone.title)
        assertEquals(listOf("BJÖRK", "Ghost"), gone.artists)
        assertEquals(2, gone.plays)
        assertEquals(
            listOf(Triple(1L, "Björk", 3), Triple(null, "Ghost", 2)),
            summary.artists.map {
                Triple(it.artistId, it.name, it.plays)
            },
        )
        assertEquals(emptyList<SummaryAlbum>(), summary.albums)
    }

    @Test
    fun `the same title of other deleted artists is another track`() {
        val listens = listOf(listen(null, "Intro", listOf("A")), listen(null, "Intro", listOf("B")))
        assertEquals(2, calculate(listens)!!.tracks.size)
    }

    @Test
    fun `a track without genre and album still counts for tracks and artists`() {
        val summary = calculate(listOf(listen(1)), mapOf(1L to listOf(burial)))!!
        assertNull(summary.favoriteGenre)
        assertNull(summary.artists.single().image)
        assertEquals(1, summary.tracks.size)
    }

    @Test
    fun `the favorite genre counts each genre of a track once per listen`() {
        val listens = listOf(listen(1), listen(1), listen(2), listen(3))
        val genres =
            mapOf(1L to listOf("Trip hop", "trip-hop", "Electronic"), 2L to listOf("electronic"), 3L to listOf("Jazz"))
        assertEquals("Electronic", calculate(listens, genres = genres)!!.favoriteGenre)
    }

    @Test
    fun `an artist shows the photo, else the cover of the album of most of its listens`() {
        val listens = listOf(listen(1, album = 10), listen(2, album = 20), listen(2, album = 20), listen(3, album = 30))
        val artists = mapOf(1L to listOf(bjork), 2L to listOf(bjork), 3L to listOf(thom))
        val summary = calculate(listens, artists)!!
        val images = summary.artists.associate { it.name to it.image }

        assertEquals(ArtistImage.AlbumCover(20), images["Björk"])
        assertEquals(ArtistImage.Photo("/photos/thom.jpg"), images["Thom Yorke"])
    }

    @Test
    fun `a running period is marked so`() {
        val summary = SummaryCalculator.calculate(period, isFinished = false, listOf(listen(1)), emptyMap(), emptyMap())
        assertTrue(summary?.isFinished == false)
    }
}
