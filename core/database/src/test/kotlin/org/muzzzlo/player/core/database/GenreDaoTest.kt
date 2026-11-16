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
import org.muzzzlo.player.core.database.entity.TrackEntity
import org.muzzzlo.player.core.database.entity.TrackGenreEntity
import org.robolectric.RobolectricTestRunner

/** Genres in the database (task 2.5.4). */
@RunWith(RobolectricTestRunner::class)
class GenreDaoTest {
    private lateinit var db: MuzzzloDatabase
    private lateinit var dao: LibraryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MuzzzloDatabase::class.java).allowMainThreadQueries().build()
        dao = db.libraryDao()
    }

    @After
    fun tearDown() = db.close()

    private fun track(
        uri: String,
        title: String,
    ) = TrackEntity(
        mediaStoreId = null,
        uri = uri,
        title = title,
        titleKey = title.lowercase(),
        artistTagValues = "[]",
        durationMs = 1000,
    )

    @Test
    fun `genre is found by key and keeps its first spelling`() =
        runBlocking {
            val first = dao.genreId("Drum & Bass", "drum&bass")
            val second = dao.genreId("drum and bass", "drum&bass")
            assertEquals(first, second)
            assertEquals(listOf("Drum & Bass"), genreNames())
        }

    @Test
    fun `track genres keep the tag order and are replaced on rescan`() =
        runBlocking {
            val trackId = dao.insertTrack(track("content://1", "Song"))
            val rock = dao.genreId("Rock", "rock")
            val pop = dao.genreId("Pop", "pop")
            dao.insertTrackGenres(listOf(TrackGenreEntity(trackId, pop, 0), TrackGenreEntity(trackId, rock, 1)))
            assertEquals(listOf("Pop", "Rock"), dao.observeTrackGenres().first().map { it.name })

            dao.clearTrackGenres(trackId)
            dao.insertTrackGenres(listOf(TrackGenreEntity(trackId, rock, 0)))
            dao.deleteEmptyGenres()
            assertEquals(listOf("Rock"), dao.genreNamesFor(listOf(trackId)).map { it.name })
            assertEquals(listOf("Rock"), genreNames())
        }

    @Test
    fun `a missing track keeps its genres, a forgotten one takes them along`() =
        runBlocking {
            val trackId = dao.insertTrack(track("content://1", "Song"))
            val jazz = dao.genreId("Jazz", "jazz")
            dao.insertTrackGenres(listOf(TrackGenreEntity(trackId, jazz, 0)))

            dao.setMissing(listOf(trackId), true)
            dao.deleteEmptyGenres()
            assertEquals(listOf("Jazz"), genreNames())

            dao.markDeleted(trackId, nowMs = 0)
            dao.deleteExpiredTracks(cutoffMs = 1)
            dao.deleteEmptyGenres()
            assertEquals(emptyList<String>(), genreNames())
        }

    private fun genreNames(): List<String> =
        db.query("SELECT name FROM genre ORDER BY name", null).use {
            buildList { while (it.moveToNext()) add(it.getString(0)) }
        }
}
