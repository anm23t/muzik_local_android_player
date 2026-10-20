package org.muzzzlo.player.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.muzzzlo.player.core.database.dao.LibraryDao
import org.muzzzlo.player.core.database.dao.ListenEventDao
import org.muzzzlo.player.core.database.entity.AlbumEntity
import org.muzzzlo.player.core.database.entity.ArtistEntity
import org.muzzzlo.player.core.database.entity.ListenEventEntity
import org.muzzzlo.player.core.database.entity.TrackArtistEntity
import org.muzzzlo.player.core.database.entity.TrackEntity
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ListenEventDaoStatsTest {
    private lateinit var db: MuzzzloDatabase
    private lateinit var library: LibraryDao
    private lateinit var dao: ListenEventDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MuzzzloDatabase::class.java).allowMainThreadQueries().build()
        library = db.libraryDao()
        dao = db.listenEventDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun artist(name: String) =
        library.insertArtist(
            ArtistEntity(name = name, nameKey = name.lowercase()),
        )

    private suspend fun track(
        title: String,
        albumId: Long? = null,
        vararg artists: Long,
    ): Long {
        val id =
            library.insertTrack(
                TrackEntity(
                    mediaStoreId = null,
                    uri = "content://$title",
                    title = title,
                    titleKey = title.lowercase(),
                    artistTagValues = "[]",
                    albumId = albumId,
                    durationMs = 1000,
                ),
            )
        library.insertTrackArtists(artists.mapIndexed { i, a -> TrackArtistEntity(id, a, i) })
        return id
    }

    private suspend fun listen(
        trackId: Long?,
        at: Long,
        playedMs: Long = 100,
        counted: Boolean = true,
        skipped: Boolean = false,
        title: String = "snap",
        artists: String = "[]",
    ) = dao.insert(
        ListenEventEntity(
            trackId = trackId,
            startedAt = at,
            playedMs = playedMs,
            durationMs = 1000,
            counted = counted,
            skipped = skipped,
            titleSnapshot = title,
            artistsSnapshot = artists,
        ),
    )

    private val all = Long.MIN_VALUE to Long.MAX_VALUE

    @Test
    fun totalsCountOnlyTheRangeAndSeparateSkips() =
        runBlocking {
            val t = track("a")
            listen(t, at = 10, playedMs = 100)
            listen(t, at = 20, playedMs = 50, counted = false, skipped = true)
            listen(t, at = 30, playedMs = 5, counted = false)
            listen(t, at = 99, playedMs = 700)
            val totals = dao.observeTotals(10, 99).first()
            assertEquals(1, totals.plays)
            assertEquals(1, totals.skips)
            assertEquals(100L, totals.playedMs)
        }

    @Test
    fun totalsOfNothingAreZero() =
        runBlocking {
            val totals = dao.observeTotals(all.first, all.second).first()
            assertEquals(0, totals.plays)
            assertEquals(0L, totals.playedMs)
        }

    @Test
    fun rangeIsHalfOpen() =
        runBlocking {
            val t = track("a")
            listen(t, at = 10)
            listen(t, at = 20)
            assertEquals(1, dao.observeTopTracks(10, 20, 5).first().single().plays)
            assertEquals(2, dao.observeTopTracks(10, 21, 5).first().single().plays)
        }

    @Test
    fun topTracksOrderByPlaysThenTimeThenTitle() =
        runBlocking {
            val a = track("Alpha")
            val b = track("Beta")
            val c = track("Cargo")
            val d = track("delta")
            listen(a, 1, playedMs = 10)
            listen(b, 2, playedMs = 10)
            listen(b, 3, playedMs = 10)
            listen(c, 4, playedMs = 90)
            listen(d, 5, playedMs = 90)
            listen(d, 6, playedMs = 0, counted = false, skipped = true)
            val titles = dao.observeTopTracks(all.first, all.second, 5).first().map { it.title }
            assertEquals(listOf("Beta", "Cargo", "delta", "Alpha"), titles)
        }

    @Test
    fun topTracksRespectTheLimit() =
        runBlocking {
            repeat(7) { listen(track("t$it"), it.toLong()) }
            assertEquals(5, dao.observeTopTracks(all.first, all.second, 5).first().size)
        }

    @Test
    fun deletedTrackStaysInTopBySnapshot() =
        runBlocking {
            val gone = track("Old name")
            listen(gone, 1, title = "Old name", artists = "[\"X\"]")
            listen(gone, 2, title = "Old name", artists = "[\"X\"]")
            db.openHelper.writableDatabase.execSQL("DELETE FROM track WHERE id = ?", arrayOf<Any>(gone))
            listen(null, 3, title = "Old name", artists = "[\"X\"]")
            listen(null, 4, title = "Other", artists = "[\"X\"]")
            val top = dao.observeTopTracks(all.first, all.second, 5).first()
            assertEquals(listOf("Old name", "Other"), top.map { it.title })
            assertEquals(listOf(3, 1), top.map { it.plays })
        }

    @Test
    fun topAlbumsSumTracksAndSkipTracksWithoutAlbum() =
        runBlocking {
            val album1 = library.insertAlbum(AlbumEntity(title = "One", titleKey = "one"))
            val album2 = library.insertAlbum(AlbumEntity(title = "Two", titleKey = "two"))
            val a = track("a", album1)
            val b = track("b", album1)
            val c = track("c", album2)
            val single = track("s", null)
            listen(a, 1)
            listen(b, 2)
            listen(c, 3)
            listen(c, 4, counted = false, skipped = true)
            listen(single, 5)
            val top = dao.observeTopAlbums(all.first, all.second, 5).first()
            assertEquals(listOf("One", "Two"), top.map { it.title })
            assertEquals(listOf(2, 1), top.map { it.plays })
        }

    @Test
    fun everyArtistOfATrackGetsTheListen() =
        runBlocking {
            val x = artist("X")
            val y = artist("Y")
            val duet = track("duet", null, x, y)
            val solo = track("solo", null, x)
            listen(duet, 1)
            listen(solo, 2)
            val plays = dao.observeArtistPlays(all.first, all.second).first().associate { it.name to it.plays }
            assertEquals(mapOf("X" to 2, "Y" to 1), plays)
        }

    @Test
    fun orphanListensExposeTheArtistSnapshot() =
        runBlocking {
            val t = track("t", null, artist("X"))
            listen(t, 1)
            listen(null, 2, artists = "[\"A\",\"B\"]")
            listen(null, 3, artists = "[\"A\"]", counted = false)
            val rows = dao.observeOrphanArtistPlays(all.first, all.second).first()
            assertEquals(listOf("[\"A\",\"B\"]"), rows.map { it.artistsSnapshot })
        }

    @Test
    fun recentAlbumsGoByTheLatestListenAndSkipsCount() =
        runBlocking {
            val one = library.insertAlbum(AlbumEntity(title = "One", titleKey = "one"))
            val two = library.insertAlbum(AlbumEntity(title = "Two", titleKey = "two"))
            val three = library.insertAlbum(AlbumEntity(title = "Three", titleKey = "three"))
            val a = track("a", one)
            val b = track("b", two)
            val c = track("c", three)
            listen(a, 10)
            listen(b, 20, counted = false, skipped = true)
            listen(c, 30)
            listen(a, 40)
            assertEquals(listOf(one, three, two), dao.observeRecentAlbumIds(5).first())
        }

    @Test
    fun recentAlbumsRespectTheLimit() =
        runBlocking {
            val albums = (1..4).map { library.insertAlbum(AlbumEntity(title = "A$it", titleKey = "a$it")) }
            albums.forEachIndexed { i, album -> listen(track("t$i", album), i + 1L) }
            assertEquals(albums.reversed().take(2), dao.observeRecentAlbumIds(2).first())
        }

    @Test
    fun recentAlbumsLeaveOutTracksWithoutAlbumAndDeletedTracks() =
        runBlocking {
            val album = library.insertAlbum(AlbumEntity(title = "One", titleKey = "one"))
            listen(track("loose", null), 5)
            listen(null, 6)
            listen(track("a", album), 1)
            assertEquals(listOf(album), dao.observeRecentAlbumIds(5).first())
        }

    @Test
    fun noListensGiveNoAlbums() =
        runBlocking {
            assertEquals(emptyList<Long>(), dao.observeRecentAlbumIds(5).first())
        }
}
