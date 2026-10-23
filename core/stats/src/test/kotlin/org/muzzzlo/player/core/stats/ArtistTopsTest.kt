package org.muzzzlo.player.core.stats

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.muzzzlo.player.core.database.dao.ArtistPlaysRow
import org.muzzzlo.player.core.database.dao.OrphanPlayRow
import org.muzzzlo.player.core.model.TopArtist

class ArtistTopsTest {
    @Test
    fun snapshotNamesTable() {
        val cases =
            mapOf(
                "[]" to emptyList(),
                "" to emptyList(),
                "not json" to emptyList(),
                "[\"A\"]" to listOf("A"),
                "[\"A\",\"B\"]" to listOf("A", "B"),
                "[\"Say \\\"hi\\\"\",\"C\\\\D\"]" to listOf("Say \"hi\"", "C\\D"),
                "[\"Tyler, The Creator\"]" to listOf("Tyler, The Creator"),
            )
        for ((json, names) in cases) assertEquals(names, snapshotNames(json), json)
    }

    @Test
    fun orphansJoinLibraryArtistsByNormalizedName() {
        val linked = listOf(ArtistPlaysRow(1, "Daft Punk", 2, 200))
        val orphans = listOf(OrphanPlayRow("[\"daft punk\",\"Pharrell\"]", 50))
        val top = mergeTopArtists(linked, orphans, 5)
        assertEquals(
            listOf(TopArtist(1, "Daft Punk", 3, 250), TopArtist(null, "Pharrell", 1, 50)),
            top,
        )
    }

    @Test
    fun theLibraryArtistKeepsItsPhotoWhenOrphansJoinIt() {
        val linked = listOf(ArtistPlaysRow(1, "Daft Punk", 2, 200, "/files/dp.jpg"))
        val orphans = listOf(OrphanPlayRow("[\"daft punk\"]", 50))

        val top = mergeTopArtists(linked, orphans, 5)

        assertEquals(listOf(TopArtist(1, "Daft Punk", 3, 250, "/files/dp.jpg")), top)
    }

    @Test
    fun repeatedNameInOneSnapshotCountsOnce() {
        val top = mergeTopArtists(emptyList(), listOf(OrphanPlayRow("[\"A\",\"a\"]", 10)), 5)
        assertEquals(listOf(TopArtist(null, "A", 1, 10)), top)
    }

    @Test
    fun orderIsPlaysThenTimeThenName() {
        val linked =
            listOf(
                ArtistPlaysRow(1, "b", 1, 10),
                ArtistPlaysRow(2, "C", 3, 5),
                ArtistPlaysRow(3, "a", 1, 10),
                ArtistPlaysRow(4, "d", 1, 99),
            )
        assertEquals(listOf("C", "d", "a", "b"), mergeTopArtists(linked, emptyList(), 5).map { it.name })
    }

    @ParameterizedTest(name = "limit {0}")
    @CsvSource("0,0", "1,1", "2,2", "9,3")
    fun limitCutsTheList(
        limit: Int,
        size: Int,
    ) {
        val linked = (1L..3L).map { ArtistPlaysRow(it, "a$it", 1, 1) }
        assertEquals(size, mergeTopArtists(linked, emptyList(), limit).size)
    }

    @Test
    fun noListensGiveNothing() {
        assertEquals(emptyList<TopArtist>(), mergeTopArtists(emptyList(), emptyList(), 5))
    }
}
