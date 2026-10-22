package org.muzzzlo.player.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.muzzzlo.player.core.database.dao.LibraryDao
import org.muzzzlo.player.core.database.dao.PlaylistDao
import org.muzzzlo.player.core.database.entity.PlaylistEntity
import org.muzzzlo.player.core.database.entity.TrackEntity
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistDaoTest {
    private lateinit var db: MuzzzloDatabase
    private lateinit var library: LibraryDao
    private lateinit var dao: PlaylistDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MuzzzloDatabase::class.java).allowMainThreadQueries().build()
        library = db.libraryDao()
        dao = db.playlistDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun track(title: String): Long =
        library.insertTrack(
            TrackEntity(
                mediaStoreId = null,
                uri = "content://$title",
                title = title,
                titleKey = title.lowercase(),
                artistTagValues = "[]",
                albumId = null,
                durationMs = 1000,
            ),
        )

    private suspend fun playlist(
        name: String,
        createdAt: Long = 1,
    ) = dao.insertPlaylist(PlaylistEntity(name = name, createdAt = createdAt))

    private suspend fun titles(playlistId: Long) = dao.observeEntries(playlistId).first().map { it.track.title }

    @Test
    fun `append keeps order and allows the same track twice`() =
        runBlocking {
            val a = track("A")
            val b = track("B")
            val p = playlist("Mix")

            dao.append(p, listOf(a, b))
            dao.append(p, listOf(a))

            assertEquals(listOf("A", "B", "A"), titles(p))
        }

    @Test
    fun `playlists come in name order with counts and the first track`() =
        runBlocking {
            val a = track("A")
            val b = track("B")
            val zed = playlist("zed")
            val alpha = playlist("Alpha")
            dao.append(zed, listOf(b, a))

            val rows = dao.observePlaylists().first()

            assertEquals(listOf("Alpha", "zed"), rows.map { it.name })
            assertEquals(0, rows[0].trackCount)
            assertNull(rows[0].firstTrackId)
            assertEquals(2, rows[1].trackCount)
            assertEquals(b, rows[1].firstTrackId)
            assertEquals(alpha, rows[0].id)
        }

    @Test
    fun `entries of missing files are hidden, counted out and come back`() =
        runBlocking {
            val a = track("A")
            val b = track("B")
            val p = playlist("Mix")
            dao.append(p, listOf(a, b))

            library.setMissing(listOf(a), true)

            assertEquals(listOf("B"), titles(p))
            val row = dao.observePlaylist(p).first()!!
            assertEquals(1, row.trackCount)
            assertEquals(b, row.firstTrackId)
            assertEquals(listOf(false, true), dao.slots(p).map { it.visible })

            library.setMissing(listOf(a), false)
            assertEquals(listOf("A", "B"), titles(p))
        }

    @Test
    fun `set order renumbers entries`() =
        runBlocking {
            val ids = listOf(track("A"), track("B"), track("C"))
            val p = playlist("Mix")
            dao.append(p, ids)
            val entries = dao.slots(p).map { it.id }

            dao.setOrder(listOf(entries[2], entries[0], entries[1]))

            assertEquals(listOf("C", "A", "B"), titles(p))
            dao.append(p, listOf(ids[0]))
            assertEquals(listOf("C", "A", "B", "A"), titles(p))
        }

    @Test
    fun `removing one entry keeps the other copy of the track`() =
        runBlocking {
            val a = track("A")
            val p = playlist("Mix")
            dao.append(p, listOf(a, a))

            dao.deleteEntry(dao.slots(p).first().id)

            assertEquals(listOf("A"), titles(p))
        }

    @Test
    fun `rename, last played and delete`() =
        runBlocking {
            val a = track("A")
            val p = playlist("Mix")
            dao.append(p, listOf(a))

            dao.rename(p, "Road")
            dao.setLastPlayed(p, 42)
            val row = dao.observePlaylist(p).first()!!
            assertEquals("Road", row.name)
            assertEquals(42L, row.lastPlayedAt)

            dao.deletePlaylist(p)
            assertNull(dao.observePlaylist(p).first())
            assertEquals(emptyList<Any>(), dao.slots(p))
        }
}
