package org.muzzzlo.player.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.muzzzlo.player.core.database.entity.AlbumEntity
import org.muzzzlo.player.core.database.entity.ArtistEntity
import org.muzzzlo.player.core.database.entity.GenreEntity
import org.muzzzlo.player.core.database.entity.ListenEventEntity
import org.muzzzlo.player.core.database.entity.TrackArtistEntity
import org.muzzzlo.player.core.database.entity.TrackEntity
import org.muzzzlo.player.core.database.entity.TrackGenreEntity
import org.robolectric.RobolectricTestRunner

/** The queries of period summaries (task 2.5.11). */
@RunWith(RobolectricTestRunner::class)
class SummaryDaoTest {
    private lateinit var db: MuzzzloDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MuzzzloDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun track(
        id: Long,
        title: String,
        albumId: Long? = null,
    ) = db.libraryDao().insertTrack(
        TrackEntity(
            id = id,
            mediaStoreId = null,
            uri = "content://$id",
            title = title,
            titleKey = title.lowercase(),
            artistTagValues = "[]",
            albumId = albumId,
            durationMs = 1000,
        ),
    )

    private fun event(
        trackId: Long?,
        startedAt: Long,
        counted: Boolean = true,
    ) = ListenEventEntity(
        trackId = trackId,
        startedAt = startedAt,
        playedMs = 1000,
        durationMs = 1000,
        counted = counted,
        skipped = !counted,
        titleSnapshot = "Old title",
        artistsSnapshot = "[\"Old\"]",
    )

    @Test
    fun `listens of the range only, counted ones, with the album and the current title`() =
        runBlocking {
            val album = db.libraryDao().insertAlbum(AlbumEntity(title = "Post", titleKey = "post"))
            track(1, "Army of Me", album)
            listOf(event(1, 99), event(1, 100), event(null, 150), event(1, 160, counted = false), event(1, 200))
                .forEach { db.listenEventDao().insert(it) }

            val listens = db.listenEventDao().summaryListens(100, 200)
            assertEquals(listOf(1L, null), listens.map { it.trackId })
            assertEquals(listOf("Army of Me", "Old title"), listens.map { it.title })
            assertEquals(listOf(album, null), listens.map { it.albumId })
            assertEquals(listOf("Post", null), listens.map { it.albumTitle })
            assertTrue(db.listenEventDao().observeHasListens(100, 200).first())
            assertFalse(db.listenEventDao().observeHasListens(160, 199).first())
        }

    @Test
    fun `artists and genres of the listened tracks in tag order`() =
        runBlocking {
            track(1, "A")
            track(2, "B")
            val bjork =
                db.libraryDao().insertArtist(
                    ArtistEntity(name = "Björk", nameKey = "bjork", photoUri = "/p.jpg"),
                )
            val thom = db.libraryDao().insertArtist(ArtistEntity(name = "Thom", nameKey = "thom"))
            db.libraryDao().insertTrackArtists(
                listOf(TrackArtistEntity(1, thom, 1), TrackArtistEntity(1, bjork, 0), TrackArtistEntity(2, thom, 0)),
            )
            val jazz = db.libraryDao().insertGenre(GenreEntity(name = "Jazz", nameKey = "jazz"))
            val pop = db.libraryDao().insertGenre(GenreEntity(name = "Pop", nameKey = "pop"))
            db.libraryDao().insertTrackGenres(listOf(TrackGenreEntity(1, pop, 1), TrackGenreEntity(1, jazz, 0)))
            db.listenEventDao().insert(event(1, 100))

            val artists = db.listenEventDao().summaryTrackArtists(0, 1000)
            assertEquals(listOf("Björk", "Thom"), artists.map { it.name })
            assertEquals(listOf("/p.jpg", null), artists.map { it.photoPath })
            assertEquals(listOf("Jazz", "Pop"), db.listenEventDao().summaryTrackGenres(0, 1000).map { it.name })
        }
}
