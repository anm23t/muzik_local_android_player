package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ContinueListeningTest {
    private fun album(id: Long) = Album(id, "A$id", emptyList(), null, 1)

    private fun playlist(
        id: Long,
        playedAt: Long?,
        trackCount: Int = 3,
    ) = Playlist(id, "P$id", trackCount, 1, 0, playedAt)

    private val library = (1L..8L).map(::album)

    /** Albums listened in this order, the first one most recently: times 1000, 990, 980… */
    private fun history(vararg ids: Long) = ids.mapIndexed { i, id -> RecentAlbum(id, 1000L - 10 * i) }

    /** Tiles as "a3" for album 3 and "p2" for playlist 2. */
    private fun tiles(
        recent: List<RecentAlbum>,
        playlists: List<Playlist> = emptyList(),
        queue: Album? = null,
        limit: Int = ContinueListening.MAX_TILES,
    ) = ContinueListening.items(recent, library, playlists, queue, limit).map {
        when (it) {
            is ContinueItem.AlbumItem -> "a${it.album.id}"
            is ContinueItem.PlaylistItem -> "p${it.playlist.id}"
        }
    }

    @Test
    fun `history order is kept`() {
        assertEquals(listOf("a3", "a1", "a2"), tiles(history(3, 1, 2)))
    }

    @Test
    fun `no more than the limit`() {
        assertEquals(listOf("a8", "a7", "a6", "a5", "a4"), tiles(history(8, 7, 6, 5, 4, 3, 2, 1)))
        assertEquals(listOf("a8", "a7"), tiles(history(8, 7, 6), limit = 2))
    }

    @Test
    fun `an album that is gone is skipped and the next one takes its place`() {
        assertEquals(listOf("a2", "a3"), tiles(history(99, 2, 3)))
    }

    @Test
    fun `repeated ids show once`() {
        assertEquals(listOf("a2", "a1"), tiles(history(2, 2, 1, 2)))
    }

    @Test
    fun `playlists go between albums by the time they were started`() {
        val playlists = listOf(playlist(1, playedAt = 995), playlist(2, playedAt = 975))

        assertEquals(listOf("a3", "p1", "a1", "a2", "p2"), tiles(history(3, 1, 2), playlists))
    }

    @Test
    fun `a playlist and an album of the same moment put the playlist first`() {
        assertEquals(listOf("p1", "a3"), tiles(history(3), listOf(playlist(1, playedAt = 1000))))
    }

    @Test
    fun `playlists count toward the limit`() {
        val playlists = (1L..4L).map { playlist(it, playedAt = 2000 + it) }

        assertEquals(listOf("p4", "p3", "p2", "p1", "a5"), tiles(history(5, 6, 7), playlists))
    }

    @Test
    fun `playlists never started or without tracks are not shown`() {
        val playlists = listOf(playlist(1, playedAt = null), playlist(2, playedAt = 2000, trackCount = 0))

        assertEquals(listOf("a1"), tiles(history(1), playlists))
    }

    @Test
    fun `an empty history shows the album of the restored queue`() {
        assertEquals(listOf("a4"), tiles(emptyList(), queue = album(4)))
    }

    @Test
    fun `a history of vanished albums also falls back to the queue`() {
        assertEquals(listOf("a4"), tiles(history(98, 99), queue = album(4)))
    }

    @Test
    fun `the queue album is not added to a history or to a played playlist`() {
        assertEquals(listOf("a1"), tiles(history(1), queue = album(4)))
        assertEquals(listOf("p1"), tiles(emptyList(), listOf(playlist(1, playedAt = 5)), queue = album(4)))
    }

    @Test
    fun `nothing at all gives an empty row`() {
        assertEquals(emptyList<String>(), tiles(emptyList()))
    }
}
