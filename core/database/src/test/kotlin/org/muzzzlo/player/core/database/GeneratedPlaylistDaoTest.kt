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
import org.muzzzlo.player.core.database.entity.AlbumEntity
import org.muzzzlo.player.core.database.entity.GeneratedPlaylistEntity
import org.muzzzlo.player.core.database.entity.ListenEventEntity
import org.muzzzlo.player.core.database.entity.PlaylistEntity
import org.muzzzlo.player.core.database.entity.TrackEntity
import org.robolectric.RobolectricTestRunner

/** Generated playlists and what they change around them (task 2.5.7). */
@RunWith(RobolectricTestRunner::class)
class GeneratedPlaylistDaoTest {
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
        missing: Boolean = false,
        albumId: Long? = null,
    ): Long =
        db.libraryDao().insertTrack(
            TrackEntity(
                id = id,
                mediaStoreId = null,
                uri = "content://$id",
                title = "Song $id",
                titleKey = "song $id",
                artistTagValues = "[]",
                albumId = albumId,
                durationMs = 1000,
                isMissing = missing,
            ),
        )

    private fun set(
        kind: String,
        day: Long = 100,
        createdAt: Long = 1000,
    ) = GeneratedPlaylistEntity(kind = kind, day = day, gradient = 3, createdAt = createdAt)

    private fun event(
        trackId: Long,
        startedAt: Long,
        sourceKind: String? = null,
    ) = ListenEventEntity(
        trackId = trackId,
        startedAt = startedAt,
        playedMs = 1000,
        durationMs = 1000,
        counted = true,
        skipped = false,
        titleSnapshot = "Song",
        artistsSnapshot = "[]",
        sourceKind = sourceKind,
    )

    @Test
    fun `a new set replaces the old one of its kind with its tracks`() =
        runBlocking {
            val dao = db.generatedPlaylistDao()
            (1L..4L).forEach { track(it) }
            dao.replace(set("onRepeat", day = 99), listOf(1, 2))
            dao.replace(set("onRepeat", day = 100), listOf(4, 3, 1))
            dao.replace(set("bestRated"), listOf(2))

            assertEquals(listOf(4L, 3L, 1L), dao.trackIds("onRepeat"))
            assertEquals(100L, dao.byKind("onRepeat")?.day)
            assertEquals(2, dao.all().size)
            assertEquals(listOf(4L, 3L, 1L), dao.observeTracks("onRepeat").first().map { it.id })
        }

    @Test
    fun `counts skip missing files and the latest listen counts only from the set`() =
        runBlocking {
            val dao = db.generatedPlaylistDao()
            track(1)
            track(2, missing = true)
            track(3)
            dao.replace(set("onRepeat", createdAt = 1000), listOf(1, 2, 3))
            db.listenEventDao().insert(event(1, startedAt = 900, sourceKind = "onRepeat"))
            db.listenEventDao().insert(event(3, startedAt = 1200, sourceKind = "bestRated"))

            val row = dao.observe("onRepeat").first()
            assertEquals(2, row?.trackCount)
            assertNull(row?.lastStartedAt)
            assertEquals(listOf(1L, 3L), dao.trackIds("onRepeat"))

            db.listenEventDao().insert(event(3, startedAt = 1500, sourceKind = "onRepeat"))
            assertEquals(1500L, dao.observe("onRepeat").first()?.lastStartedAt)
        }

    @Test
    fun `a deleted track leaves the set`() =
        runBlocking {
            val dao = db.generatedPlaylistDao()
            track(1)
            track(2)
            dao.replace(set("onRepeat"), listOf(1, 2))
            db.libraryDao().markDeleted(1, nowMs = 0)
            db.libraryDao().deleteExpiredTracks(cutoffMs = 1)
            assertEquals(listOf(2L), dao.trackIds("onRepeat"))
        }

    @Test
    fun `listens from a generated playlist do not move the album up`() =
        runBlocking {
            val album = db.libraryDao().insertAlbum(AlbumEntity(title = "A", titleKey = "a"))
            val other = db.libraryDao().insertAlbum(AlbumEntity(title = "B", titleKey = "b"))
            track(1, albumId = album)
            track(2, albumId = other)
            db.listenEventDao().insert(event(1, startedAt = 10))
            db.listenEventDao().insert(event(2, startedAt = 20, sourceKind = "onRepeat"))

            assertEquals(listOf(album), db.listenEventDao().observeRecentAlbums(5).first().map { it.albumId })
        }

    @Test
    fun `a kept generated playlist keeps its gradient`() =
        runBlocking {
            val id =
                db.playlistDao().insertPlaylist(
                    PlaylistEntity(name = "On Repeat", createdAt = 1, coverGradient = 7),
                )
            assertEquals(7, db.playlistDao().observePlaylist(id).first()?.coverGradient)
        }

    @Test
    fun `the daily mix reads counted listens oldest first with their source`() =
        runBlocking {
            track(1)
            db.listenEventDao().insert(event(1, startedAt = 30, sourceKind = "dailyMix"))
            db.listenEventDao().insert(event(1, startedAt = 10))
            db.listenEventDao().insert(event(1, startedAt = 20).copy(counted = false, skipped = true))

            val listens = db.listenEventDao().countedListens()
            assertEquals(listOf(10L, 30L), listens.map { it.startedAt })
            assertEquals(listOf(null, "dailyMix"), listens.map { it.sourceKind })
        }
}
