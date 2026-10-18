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
import org.muzzzlo.player.core.database.dao.LibraryDao
import org.muzzzlo.player.core.database.entity.AlbumArtistEntity
import org.muzzzlo.player.core.database.entity.AlbumEntity
import org.muzzzlo.player.core.database.entity.TrackArtistEntity
import org.muzzzlo.player.core.database.entity.TrackEntity
import org.muzzzlo.player.core.database.entity.TrackScanFields
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryDaoTest {
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
        albumId: Long? = null,
        number: Int? = null,
    ) = TrackEntity(
        mediaStoreId = null,
        uri = uri,
        title = title,
        titleKey = title.lowercase(),
        artistTagValues = "[]",
        albumId = albumId,
        trackNumber = number,
        durationMs = 1000,
    )

    @Test
    fun `artist is found by key instead of duplicated`() =
        runBlocking {
            val first = dao.artistId("Eminem", "eminem")
            val second = dao.artistId("EMINEM", "eminem")
            assertEquals(first, second)
        }

    @Test
    fun `track artists keep their order`() =
        runBlocking {
            val a = dao.artistId("A", "a")
            val b = dao.artistId("B", "b")
            val trackId = dao.insertTrack(track("u1", "Song"))
            dao.insertTrackArtists(
                listOf(TrackArtistEntity(trackId, b, 1), TrackArtistEntity(trackId, a, 0)),
            )
            val names = dao.observeTrackArtists().first().map { it.name }
            assertEquals(listOf("A", "B"), names)
        }

    @Test
    fun `missing tracks are hidden from lists`() =
        runBlocking {
            val id = dao.insertTrack(track("u1", "Song"))
            dao.setMissing(listOf(id), true)
            assertTrue(dao.observeTracks().first().isEmpty())
            dao.setMissing(listOf(id), false)
            assertEquals(1, dao.observeTracks().first().size)
        }

    @Test
    fun `album tracks are ordered by number and counted`() =
        runBlocking {
            val album = dao.insertAlbum(AlbumEntity(title = "Album", titleKey = "album"))
            val artist = dao.artistId("A", "a")
            dao.insertAlbumArtists(listOf(AlbumArtistEntity(album, artist, 0)))
            dao.insertTrack(track("u2", "Second", album, 2))
            dao.insertTrack(track("u1", "First", album, 1))
            assertEquals(listOf("First", "Second"), dao.observeAlbumTracks(album).first().map { it.title })
            assertEquals(2, dao.observeAlbums().first().single().trackCount)
        }

    @Test
    fun `empty albums are deleted`() =
        runBlocking {
            dao.insertAlbum(AlbumEntity(title = "Lonely", titleKey = "lonely"))
            dao.deleteEmptyAlbums()
            assertTrue(dao.observeAlbums().first().isEmpty())
        }

    @Test
    fun `scan update keeps rating and favorite`() =
        runBlocking {
            val id = dao.insertTrack(track("u1", "Old").copy(ratingTenths = 67, isFavorite = true, dateModified = 1))
            dao.setMissing(listOf(id), true)

            dao.updateScannedTrack(
                TrackScanFields(
                    id = id,
                    mediaStoreId = 5,
                    uri = "u1",
                    relativePath = "Music/",
                    title = "New",
                    titleKey = "new",
                    artistTagValues = "[\"A\"]",
                    albumId = null,
                    trackNumber = 3,
                    discNumber = null,
                    year = 2001,
                    durationMs = 2000,
                    format = "FLAC",
                    bitrate = 900,
                    fileSize = 10,
                    dateAdded = 1,
                    dateModified = 2,
                    hasCover = true,
                    hasLyrics = false,
                    isDamaged = false,
                ),
            )

            val updated = dao.tracksByIds(listOf(id)).single()
            assertEquals(67, updated.ratingTenths)
            assertTrue(updated.isFavorite)
            assertEquals("New", updated.title)
            assertEquals(2L, updated.dateModified)
            assertFalse(updated.isMissing)
        }
}
