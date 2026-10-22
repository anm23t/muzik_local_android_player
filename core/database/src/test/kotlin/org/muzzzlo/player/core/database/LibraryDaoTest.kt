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
import org.muzzzlo.player.core.database.dao.ArtistAlbumRef
import org.muzzzlo.player.core.database.dao.LibraryDao
import org.muzzzlo.player.core.database.dao.TrackArtistRow
import org.muzzzlo.player.core.database.entity.AlbumArtistEntity
import org.muzzzlo.player.core.database.entity.AlbumEntity
import org.muzzzlo.player.core.database.entity.ArtistAliasEntity
import org.muzzzlo.player.core.database.entity.ArtistCompoundEntity
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
        trackTotal: Int? = null,
        discNumber: Int? = null,
        discTotal: Int? = null,
        isMissing: Boolean = false,
        rating: Int? = null,
    ) = TrackEntity(
        mediaStoreId = null,
        uri = uri,
        title = title,
        titleKey = title.lowercase(),
        artistTagValues = "[]",
        albumId = albumId,
        trackNumber = number,
        discNumber = discNumber,
        trackTotal = trackTotal,
        discTotal = discTotal,
        durationMs = 1000,
        isMissing = isMissing,
        ratingTenths = rating,
    )

    @Test
    fun `artist is found by key instead of duplicated`() =
        runBlocking {
            val first = dao.artistId("Eminem", "eminem")
            val second = dao.artistId("EMINEM", "eminem")
            assertEquals(first, second)
        }

    @Test
    fun `alias sends a spelling to its artist`() =
        runBlocking {
            val jay = dao.artistId("Jay-Z", "jay-z")
            dao.insertAlias(ArtistAliasEntity(aliasKey = "jay z", aliasDisplay = "Jay Z", artistId = jay))
            assertEquals(jay, dao.artistId("Jay Z", "jay z"))
            assertEquals(jay, dao.artistId("Jay-Z", "jay-z"))
        }

    @Test
    fun `tracks of an artist include tracks of their albums but not missing ones`() =
        runBlocking {
            val anna = dao.artistId("Anna", "anna")
            val album = dao.insertAlbum(AlbumEntity(title = "Album", titleKey = "album"))
            dao.insertAlbumArtists(listOf(AlbumArtistEntity(album, anna, 0)))
            val direct = dao.insertTrack(track("u1", "Direct"))
            val viaAlbum = dao.insertTrack(track("u2", "Via album", album))
            val gone = dao.insertTrack(track("u3", "Gone", album))
            dao.insertTrack(track("u4", "Other"))
            dao.insertTrackArtists(listOf(TrackArtistEntity(direct, anna, 0)))
            dao.setMissing(listOf(gone), true)

            assertEquals(listOf(direct, viaAlbum), dao.trackIdsOfArtist(anna))
        }

    @Test
    fun `orphan artist is deleted with aliases, one with tracks or a photo stays`() =
        runBlocking {
            val orphan = dao.artistId("Orphan", "orphan")
            val withPhoto = dao.artistId("Photo", "photo")
            val withTrack = dao.artistId("Track", "track")
            dao.setArtistPhotoPath(withPhoto, "p.jpg")
            dao.insertTrackArtists(listOf(TrackArtistEntity(dao.insertTrack(track("u1", "S")), withTrack, 0)))
            dao.insertAlias(ArtistAliasEntity(aliasKey = "o", aliasDisplay = "O", artistId = orphan))

            listOf(orphan, withPhoto, withTrack).forEach { dao.deleteArtistIfOrphan(it) }

            assertEquals(null, dao.artistName(orphan))
            assertEquals(null, dao.aliasArtistId("o"))
            assertEquals("Photo", dao.artistName(withPhoto))
            assertEquals("Track", dao.artistName(withTrack))
        }

    @Test
    fun `aliases move to another artist and can be dropped by key`() =
        runBlocking {
            val from = dao.artistId("From", "from")
            val to = dao.artistId("To", "to")
            dao.insertAlias(ArtistAliasEntity(aliasKey = "a", aliasDisplay = "A", artistId = from))
            dao.insertAlias(ArtistAliasEntity(aliasKey = "b", aliasDisplay = "B", artistId = from))

            dao.moveAliases(from, to)
            dao.deleteAliasesByKeys(listOf("b"))

            assertEquals(to, dao.aliasArtistId("a"))
            assertEquals(null, dao.aliasArtistId("b"))
        }

    @Test
    fun `merging folds credits aliases and photo into one artist and keeps the old spelling`() =
        runBlocking {
            val keep = dao.artistId("Beyoncé", "beyoncé")
            val old = dao.artistId("Beyonce", "beyonce")
            val both = dao.insertTrack(track("u1", "Both"))
            val onlyOld = dao.insertTrack(track("u2", "Old"))
            val album = dao.insertAlbum(AlbumEntity(title = "Album", titleKey = "album"))
            dao.insertTrackArtists(
                listOf(
                    TrackArtistEntity(both, keep, 0),
                    TrackArtistEntity(both, old, 1),
                    TrackArtistEntity(onlyOld, old, 0),
                ),
            )
            dao.insertAlbumArtists(listOf(AlbumArtistEntity(album, old, 0)))
            dao.insertAlias(ArtistAliasEntity(aliasKey = "bey", aliasDisplay = "Bey", artistId = old))
            dao.setArtistPhotoPath(old, "p.jpg")

            dao.mergeArtists(old, keep)

            assertEquals(null, dao.artistName(old))
            assertEquals(listOf(both, onlyOld), dao.trackIdsOfArtist(keep))
            assertEquals(listOf(keep), dao.albumArtistIds(album))
            assertEquals(keep, dao.aliasArtistId("beyonce"))
            assertEquals(keep, dao.aliasArtistId("bey"))
            assertEquals("p.jpg", dao.artistPhotoPath(keep))
            // A rescan of a file spelled the old way lands on the kept artist.
            assertEquals(keep, dao.artistId("Beyonce", "beyonce"))
        }

    @Test
    fun `merging leaves a photo of the kept artist alone and ignores merging into itself`() =
        runBlocking {
            val keep = dao.artistId("Pink", "pink")
            val old = dao.artistId("P!nk", "p!nk")
            dao.setArtistPhotoPath(keep, "keep.jpg")
            dao.setArtistPhotoPath(old, "old.jpg")

            dao.mergeArtists(keep, keep)
            assertEquals("Pink", dao.artistName(keep))

            dao.mergeArtists(old, keep)
            assertEquals("keep.jpg", dao.artistPhotoPath(keep))
        }

    private fun rule(
        name: String,
        key: String,
        builtinId: String? = null,
    ) = ArtistCompoundEntity(displayName = name, matchKey = key, builtinId = builtinId, createdAt = 1)

    @Test
    fun `compound rules are unique by key and by built-in id and can be switched off`() =
        runBlocking {
            assertTrue(dao.insertCompoundRule(rule("Tyler, The Creator", "tyler|the creator", "tyler")) > 0)
            assertEquals(-1L, dao.insertCompoundRule(rule("tyler, the creator", "tyler|the creator")))
            assertEquals(-1L, dao.insertCompoundRule(rule("Other", "other|name", "tyler")))
            val own = dao.insertCompoundRule(rule("Blood, Sweat & Tears", "blood|sweat & tears"))

            val builtin = dao.compoundRules().first { it.builtinId == "tyler" }
            dao.setCompoundRuleEnabled(builtin.id, false)
            assertEquals(listOf("blood|sweat & tears"), dao.enabledCompoundKeys())
            assertEquals(listOf("tyler"), dao.knownBuiltinIds())

            dao.deleteCompoundRule(own)
            assertEquals(1, dao.compoundRules().size)
        }

    @Test
    fun `orphan sweep keeps artists with tracks albums photos or aliases`() =
        runBlocking {
            val gone = dao.artistId("Gone", "gone")
            val withTrack = dao.artistId("Track", "track")
            val withAlbum = dao.artistId("Album", "album")
            val withPhoto = dao.artistId("Photo", "photo")
            val withAlias = dao.artistId("Alias", "alias")
            dao.insertTrackArtists(listOf(TrackArtistEntity(dao.insertTrack(track("u1", "S")), withTrack, 0)))
            dao.insertAlbumArtists(
                listOf(AlbumArtistEntity(dao.insertAlbum(AlbumEntity(title = "A", titleKey = "a")), withAlbum, 0)),
            )
            dao.setArtistPhotoPath(withPhoto, "p.jpg")
            dao.insertAlias(ArtistAliasEntity(aliasKey = "al", aliasDisplay = "Al", artistId = withAlias))

            dao.deleteOrphanArtists()

            assertEquals(null, dao.artistName(gone))
            listOf(withTrack, withAlbum, withPhoto, withAlias).forEach { assertTrue(dao.artistName(it) != null) }
        }

    @Test
    fun `raw tag values come back for present tracks and a forgotten date forces a new read`() =
        runBlocking {
            val id = dao.insertTrack(track("u1", "S").copy(artistTagValues = "[\"A, B\"]", dateModified = 5))
            val missing = dao.insertTrack(track("u2", "M"))
            dao.setMissing(listOf(missing), true)

            assertEquals(listOf(id to "[\"A, B\"]"), dao.trackArtistTagValues().map { it.id to it.artistTagValues })
            dao.forgetDateModified(listOf(id))
            assertEquals(null, dao.trackStamps().first { it.id == id }.dateModified)
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
    fun `artist album refs carry the totals of all present tracks of the album`() =
        runBlocking {
            val anna = dao.artistId("Anna", "anna")
            val album = dao.insertAlbum(AlbumEntity(title = "Own", titleKey = "own"))
            dao.insertAlbumArtists(listOf(AlbumArtistEntity(album, anna, 0)))
            dao.insertTrack(track("u1", "One", album, 1, trackTotal = 2, discNumber = 1))
            // Another artist's track still counts for the album; a missing one does not.
            dao.insertTrack(track("u2", "Two", album, 2, trackTotal = 3, discNumber = 1, discTotal = 1))
            dao.insertTrack(track("u3", "Gone", album, 3, trackTotal = 9, discNumber = 2, isMissing = true))
            val noTotals = dao.insertAlbum(AlbumEntity(title = "Bare", titleKey = "bare"))
            dao.insertAlbumArtists(listOf(AlbumArtistEntity(noTotals, anna, 0)))
            dao.insertTrack(track("u4", "Bare", noTotals, 1))

            val refs = dao.observeArtistAlbumRefs(anna).first().sortedBy { it.albumId }

            assertEquals(
                listOf(
                    ArtistAlbumRef(album, isOwn = true, maxTrackTotal = 3, maxDiscTotal = 1, maxDiscNumber = 1),
                    ArtistAlbumRef(noTotals, isOwn = true),
                ),
                refs,
            )
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
    fun `favorites come latest first and a cleared favorite leaves the list`() =
        runBlocking {
            val a = dao.insertTrack(track("u1", "Aa"))
            val b = dao.insertTrack(track("u2", "Bb"))
            val c = dao.insertTrack(track("u3", "Cc"))
            val gone = dao.insertTrack(track("u4", "Dd", isMissing = true))
            dao.setFavorite(a, true, 100)
            dao.setFavorite(b, true, 300)
            dao.setFavorite(c, true, 200)
            dao.setFavorite(gone, true, 400)
            assertEquals(listOf(b, c, a), dao.observeFavoriteTracks().first().map { it.id })

            dao.setFavorite(c, false, null)
            assertEquals(listOf(b, a), dao.observeFavoriteTracks().first().map { it.id })
            assertEquals(false, dao.marksOf(c)?.isFavorite)
        }

    @Test
    fun `favorites without a moment come after the dated ones in title order`() =
        runBlocking {
            val old2 = dao.insertTrack(track("u1", "Zz"))
            val old1 = dao.insertTrack(track("u2", "Aa"))
            val dated = dao.insertTrack(track("u3", "Mm"))
            dao.setFavorite(old2, true, 0)
            dao.setFavorite(old1, true, 0)
            dao.setFavorite(dated, true, 5)
            assertEquals(listOf(dated, old1, old2), dao.observeFavoriteTracks().first().map { it.id })
        }

    @Test
    fun `rated tracks come best first and unrated ones are left out`() =
        runBlocking {
            val low = dao.insertTrack(track("u1", "Low", rating = 12))
            val zero = dao.insertTrack(track("u2", "Zero", rating = 0))
            val topB = dao.insertTrack(track("u3", "Bb", rating = 95))
            val topA = dao.insertTrack(track("u4", "Aa", rating = 95))
            dao.insertTrack(track("u5", "None"))
            dao.insertTrack(track("u6", "Gone", rating = 100, isMissing = true))
            assertEquals(listOf(topA, topB, low, zero), dao.observeRatedTracks().first().map { it.id })

            dao.setRating(low, null)
            dao.setRating(zero, 30)
            assertEquals(listOf(topA, topB, zero), dao.observeRatedTracks().first().map { it.id })
            assertEquals(30, dao.marksOf(zero)?.ratingTenths)
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
                    trackTotal = 10,
                    discTotal = null,
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
            assertEquals(10, updated.trackTotal)
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

    @Test
    fun `artists of a track come in tag order with their photos`() =
        runBlocking {
            val first = dao.artistId("Zed", "zed")
            val second = dao.artistId("Amy", "amy")
            dao.setArtistPhotoPath(second, "amy.jpg")
            val song = dao.insertTrack(track("u1", "Song"))
            dao.insertTrackArtists(listOf(TrackArtistEntity(song, second, 1), TrackArtistEntity(song, first, 0)))

            assertEquals(
                listOf(TrackArtistRow(first, "Zed", null), TrackArtistRow(second, "Amy", "amy.jpg")),
                dao.artistsOfTrack(song),
            )
        }

    @Test
    fun `a track without artists has none`() =
        runBlocking {
            assertEquals(emptyList<TrackArtistRow>(), dao.artistsOfTrack(dao.insertTrack(track("u1", "Alone"))))
            assertEquals(emptyList<TrackArtistRow>(), dao.artistsOfTrack(9999))
        }
}
