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
import org.muzzzlo.player.core.database.dao.TrackPlayStatRow
import org.muzzzlo.player.core.database.entity.ListenEventEntity
import org.muzzzlo.player.core.database.entity.SmartPlaylistEntity
import org.muzzzlo.player.core.database.entity.TrackEntity
import org.robolectric.RobolectricTestRunner

/** Smart playlists and the listen counts their rules use (task 2.5.6). */
@RunWith(RobolectricTestRunner::class)
class SmartPlaylistDaoTest {
    private lateinit var db: MuzzzloDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MuzzzloDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `smart playlists are created, changed and deleted, oldest first`() =
        runBlocking {
            val dao = db.smartPlaylistDao()
            val second = dao.insert(SmartPlaylistEntity(name = "Late", rulesJson = "{}", createdAt = 20))
            val first = dao.insert(SmartPlaylistEntity(name = "Early", rulesJson = "{}", createdAt = 10))
            assertEquals(listOf(first, second), dao.observeAll().first().map { it.id })

            dao.update(first, "Renamed", """{"limit":5}""")
            assertEquals("Renamed", dao.byId(first)?.name)
            assertEquals("""{"limit":5}""", dao.byId(first)?.rulesJson)

            dao.delete(first)
            assertNull(dao.byId(first))
            assertEquals(listOf(second), dao.all().map { it.id })
        }

    @Test
    fun `play stats count only counted listens of existing tracks`() =
        runBlocking {
            val track =
                db.libraryDao().insertTrack(
                    TrackEntity(
                        mediaStoreId = null,
                        uri = "content://1",
                        title = "Song",
                        titleKey = "song",
                        artistTagValues = "[]",
                        durationMs = 1000,
                    ),
                )
            val events = db.listenEventDao()
            events.insert(event(track, startedAt = 10, counted = true))
            events.insert(event(track, startedAt = 30, counted = true))
            events.insert(event(track, startedAt = 50, counted = false))
            events.insert(event(null, startedAt = 70, counted = true))

            assertEquals(
                listOf(TrackPlayStatRow(track, plays = 2, lastPlayedAt = 30)),
                events.observeTrackPlayStats().first(),
            )
        }

    private fun event(
        trackId: Long?,
        startedAt: Long,
        counted: Boolean,
    ) = ListenEventEntity(
        trackId = trackId,
        startedAt = startedAt,
        playedMs = 1000,
        durationMs = 1000,
        counted = counted,
        skipped = !counted,
        titleSnapshot = "Song",
        artistsSnapshot = "[]",
    )
}
