package org.muzzzlo.player.core.stats

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.database.dao.ArtistPlaysRow
import org.muzzzlo.player.core.database.dao.ListenEventDao
import org.muzzzlo.player.core.database.dao.OrphanPlayRow
import org.muzzzlo.player.core.database.dao.TopAlbumRow
import org.muzzzlo.player.core.database.dao.TopTrackRow
import org.muzzzlo.player.core.database.dao.TotalsRow
import org.muzzzlo.player.core.database.entity.ListenEventEntity
import org.muzzzlo.player.core.model.FinishedListen
import org.muzzzlo.player.core.model.ListenEnd
import org.muzzzlo.player.core.model.StatsSettings
import org.muzzzlo.player.core.model.Track

class ListenWriterTest {
    private val dao = FakeListenEventDao()
    private val settings = MutableStateFlow(StatsSettings())
    private val writer = ListenWriter(dao, settings)

    @Test
    fun `a counted listen is stored with its snapshot`() =
        runTest {
            val event = writer.write(FinishedListen(TRACK, 1000, 120_000, ListenEnd.COMPLETED))

            assertEquals(
                ListenEventEntity(
                    id = 0,
                    trackId = 5,
                    startedAt = 1000,
                    playedMs = 120_000,
                    durationMs = 200_000,
                    counted = true,
                    skipped = false,
                    titleSnapshot = "Song",
                    artistsSnapshot = """["Tyler, The Creator","A \"B\""]""",
                ),
                dao.rows.single(),
            )
            assertEquals(1L, event?.id)
        }

    @Test
    fun `a skip is stored as not counted and skipped`() =
        runTest {
            writer.write(FinishedListen(TRACK, 1000, 30_000, ListenEnd.MANUAL))

            val row = dao.rows.single()
            assertEquals(false, row.counted)
            assertTrue(row.skipped)
        }

    @Test
    fun `the threshold from the settings applies`() =
        runTest {
            settings.value = StatsSettings(thresholdPercent = 10)
            writer.write(FinishedListen(TRACK, 1000, 20_000, ListenEnd.MANUAL))

            assertTrue(dao.rows.single().counted)
        }

    @Test
    fun `private mode and very short listens store nothing`() =
        runTest {
            writer.write(FinishedListen(TRACK, 1000, 500, ListenEnd.MANUAL))
            settings.value = StatsSettings(privateMode = true)
            val event = writer.write(FinishedListen(TRACK, 1000, 200_000, ListenEnd.COMPLETED))

            assertNull(event)
            assertEquals(emptyList<ListenEventEntity>(), dao.rows)
        }

    @Test
    fun `artists are encoded as a JSON array`() {
        assertEquals("[]", jsonArray(emptyList()))
        assertEquals("""["a\\b","line\nnext","\u0001"]""", jsonArray(listOf("a\\b", "line\nnext", "\u0001")))
    }

    private class FakeListenEventDao : ListenEventDao {
        val rows = mutableListOf<ListenEventEntity>()

        override suspend fun insert(event: ListenEventEntity): Long {
            rows += event
            return rows.size.toLong()
        }

        override suspend fun all(): List<ListenEventEntity> = rows.reversed()

        override fun observeTotals(
            from: Long,
            to: Long,
        ) = emptyFlow<TotalsRow>()

        override fun observeTopTracks(
            from: Long,
            to: Long,
            limit: Int,
        ) = emptyFlow<List<TopTrackRow>>()

        override fun observeTopAlbums(
            from: Long,
            to: Long,
            limit: Int,
        ) = emptyFlow<List<TopAlbumRow>>()

        override fun observeArtistPlays(
            from: Long,
            to: Long,
        ) = emptyFlow<List<ArtistPlaysRow>>()

        override fun observeOrphanArtistPlays(
            from: Long,
            to: Long,
        ) = emptyFlow<List<OrphanPlayRow>>()
    }

    private companion object {
        val TRACK =
            Track(
                id = 5,
                uri = "content://media/5",
                title = "Song",
                artists = listOf("Tyler, The Creator", "A \"B\""),
                albumId = null,
                albumTitle = null,
                durationMs = 200_000,
                trackNumber = null,
            )
    }
}
