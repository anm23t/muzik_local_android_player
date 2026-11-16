package org.muzzzlo.player.core.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.database.dao.AlbumRow
import org.muzzzlo.player.core.database.dao.TrackArtistName
import org.muzzzlo.player.core.database.dao.TrackGenreName
import org.muzzzlo.player.core.database.dao.TrackPlayStatRow
import org.muzzzlo.player.core.database.entity.TrackEntity

class TrackFactsTest {
    private fun entity(
        id: Long,
        missing: Boolean = false,
        favorite: Boolean = false,
        favoritedAt: Long? = null,
        albumId: Long? = null,
        dateAdded: Long? = null,
    ) = TrackEntity(
        id = id,
        mediaStoreId = id,
        uri = "content://$id",
        title = "Song $id",
        titleKey = "song $id",
        artistTagValues = "[]",
        albumId = albumId,
        year = 2001,
        durationMs = 1000,
        dateAdded = dateAdded,
        isMissing = missing,
        isFavorite = favorite,
        favoritedAt = favoritedAt,
        ratingTenths = 75,
    )

    @Test
    fun `rows join into facts and missing files are left out`() {
        val facts =
            trackFactsOf(
                tracks =
                    listOf(
                        entity(1, favorite = true, albumId = 7, dateAdded = 1_700_000_000),
                        entity(2, missing = true),
                    ),
                artists = listOf(TrackArtistName(1, "A"), TrackArtistName(1, "B"), TrackArtistName(2, "C")),
                albums = listOf(AlbumRow(7, "Album", 2001, 1, null, 0, 0)),
                genres = listOf(TrackGenreName(1, "Rock"), TrackGenreName(1, "Pop")),
                plays = listOf(TrackPlayStatRow(1, plays = 4, lastPlayedAt = 99), TrackPlayStatRow(2, 1, 5)),
            )

        val fact = facts.single()
        assertEquals(1L, fact.track.id)
        assertEquals(listOf("A", "B"), fact.track.artists)
        assertEquals("Album", fact.track.albumTitle)
        assertEquals(75, fact.track.ratingTenths)
        assertEquals(listOf("Rock", "Pop"), fact.genres)
        assertEquals(4, fact.playCount)
        assertEquals(99L, fact.lastPlayedAtMs)
        assertEquals(2001, fact.year)
        assertEquals(1_700_000_000_000L, fact.dateAddedMs)
        assertEquals(0L, fact.favoritedAtMs)
        assertEquals("song 1", fact.titleKey)
    }

    @Test
    fun `a track without listens, genres or date`() {
        val fact = trackFactsOf(listOf(entity(3)), emptyList(), emptyList(), emptyList(), emptyList()).single()
        assertEquals(0, fact.playCount)
        assertNull(fact.lastPlayedAtMs)
        assertNull(fact.dateAddedMs)
        assertNull(fact.favoritedAtMs)
        assertEquals(emptyList<String>(), fact.genres)
    }
}
