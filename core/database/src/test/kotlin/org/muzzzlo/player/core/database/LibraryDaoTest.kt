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
    fun `album date added is the earliest of its tracks`() =
        runBlocking {
            val album = dao.insertAlbum(AlbumEntity(title = "Album", titleKey = "album"))
            dao.insertTrack(track("u1", "First", album, 1).copy(dateAdded = 300))
            dao.insertTrack(track("u2", "Second", album, 2).copy(dateAdded = 100))
            assertEquals(100L, dao.observeAlbums().first().single().dateAdded)
        }

    @Test
    fun `artists are counted by present tracks and listed by name`() =
        runBlocking {
            val bob = dao.artistId("bob", "bob")
            val anna = dao.artistId("Anna", "anna")
            val ghost = dao.artistId("Ghost", "ghost")
            val first = dao.insertTrack(track("u1", "First").copy(dateAdded = 300))
            val second = dao.insertTrack(track("u2", "Second").copy(dateAdded = 100))
            val gone = dao.insertTrack(track("u3", "Gone"))
            dao.insertTrackArtists(
                listOf(
                    TrackArtistEntity(first, anna, 0),
                    TrackArtistEntity(first, bob, 1),
                    TrackArtistEntity(second, anna, 0),
                    TrackArtistEntity(gone, ghost, 0),
                ),
            )
            dao.setMissing(listOf(gone), true)

            val artists = dao.observeArtists().first()

            assertEquals(listOf("Anna", "bob"), artists.map { it.name })
            assertEquals(listOf(2, 1), artists.map { it.trackCount })
            assertEquals(100L, artists.first().dateAdded)
        }

    @Test
    fun `artist tracks are the present tracks that credit them`() =
        runBlocking {
            val anna = dao.artistId("Anna", "anna")
            val solo = dao.insertTrack(track("u1", "Solo"))
            val feature = dao.insertTrack(track("u2", "Feature"))
            val gone = dao.insertTrack(track("u3", "Gone"))
            dao.insertTrack(track("u4", "Other"))
            dao.insertTrackArtists(
                listOf(
                    TrackArtistEntity(solo, anna, 0),
                    TrackArtistEntity(feature, anna, 1),
                    TrackArtistEntity(gone, anna, 0),
                ),
            )
            dao.setMissing(listOf(gone), true)

            assertEquals(listOf("Feature", "Solo"), dao.observeArtistTracks(anna).first().map { it.title })
        }

    @Test
    fun `artist albums are their own albums and albums they appear on`() =
        runBlocking {
            val anna = dao.artistId("Anna", "anna")
            val other = dao.artistId("Other", "other")
            val own = dao.insertAlbum(AlbumEntity(title = "Own", titleKey = "own"))
            val guest = dao.insertAlbum(AlbumEntity(title = "Guest", titleKey = "guest"))
            val unrelated = dao.insertAlbum(AlbumEntity(title = "Unrelated", titleKey = "unrelated"))
            dao.insertAlbumArtists(
                listOf(
                    AlbumArtistEntity(own, anna, 0),
                    AlbumArtistEntity(guest, other, 0),
                    AlbumArtistEntity(unrelated, other, 0),
                ),
            )
            val ownTrack = dao.insertTrack(track("u1", "Own song", own, 1))
            val guestTrack = dao.insertTrack(track("u2", "Guest song", guest, 1))
            val otherTrack = dao.insertTrack(track("u3", "Other song", unrelated, 1))
            dao.insertTrackArtists(
                listOf(
                    TrackArtistEntity(ownTrack, anna, 0),
                    TrackArtistEntity(guestTrack, other, 0),
                    TrackArtistEntity(guestTrack, anna, 1),
                    TrackArtistEntity(otherTrack, other, 0),
                ),
            )

            val refs = dao.observeArtistAlbumRefs(anna).first().sortedBy { it.albumId }

            assertEquals(listOf(own to true, guest to false), refs.map { it.albumId to it.isOwn })
        }

    @Test
    fun `the artist photo path is kept and cleared`() =
        runBlocking {
            val anna = dao.artistId("Anna", "anna")
            dao.setArtistPhotoPath(anna, "/files/artist_photos/1.jpg")
            assertEquals("/files/artist_photos/1.jpg", dao.artistPhotoPath(anna))
            // A rescan finds the artist by key and keeps the photo.
            assertEquals(anna, dao.artistId("ANNA", "anna"))
            assertEquals("/files/artist_photos/1.jpg", dao.artistPhotoPath(anna))

            dao.setArtistPhotoPath(anna, null)
            assertEquals(null, dao.artistPhotoPath(anna))
        }

    @Test
    fun `a track is found by its uri`() =
        runBlocking {
            val id = dao.insertTrack(track("content://media/1", "One"))
            assertEquals(id, dao.trackIdByUri("content://media/1"))
            assertEquals(null, dao.trackIdByUri("content://media/2"))
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

    @Test
    fun `album accent keeps not computed, none and a color apart`() =
        runBlocking {
            val album = dao.insertAlbum(AlbumEntity(title = "A", titleKey = "a", year = null))
            assertEquals(null, dao.albumAccent(album))
            dao.setAlbumAccent(album, 0)
            assertEquals(0, dao.albumAccent(album))
            dao.setAlbumAccent(album, 0xFF3366CC.toInt())
            assertEquals(0xFF3366CC.toInt(), dao.albumAccent(album))
            dao.setAlbumAccent(album, null)
            assertEquals(null, dao.albumAccent(album))
        }

    @Test
    fun `the album of a track is found, none for a track without album`() =
        runBlocking {
            val album = dao.insertAlbum(AlbumEntity(title = "A", titleKey = "a", year = null))
            val inAlbum = dao.insertTrack(track("content://1", "one", albumId = album))
            val alone = dao.insertTrack(track("content://2", "two"))
            assertEquals(album, dao.albumIdOfTrack(inAlbum))
            assertEquals(null, dao.albumIdOfTrack(alone))
        }
}
