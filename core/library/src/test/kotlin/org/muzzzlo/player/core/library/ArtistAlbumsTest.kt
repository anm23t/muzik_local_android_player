package org.muzzzlo.player.core.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.database.dao.ArtistAlbumRef
import org.muzzzlo.player.core.model.Album

class ArtistAlbumsTest {
    private fun album(
        id: Long,
        title: String,
        year: Int?,
    ) = Album(id, title, listOf("A"), year, 1)

    @Test
    fun `own albums come first, each group from the newest year`() {
        val albums =
            listOf(
                album(1, "Own old", 2010),
                album(2, "Guest new", 2024),
                album(3, "Own new", 2020),
                album(4, "Own no year", null),
                album(5, "Guest old", 2001),
                album(6, "Not theirs", 2030),
            )
        val refs =
            listOf(
                ArtistAlbumRef(1, isOwn = true),
                ArtistAlbumRef(2, isOwn = false),
                ArtistAlbumRef(3, isOwn = true),
                ArtistAlbumRef(4, isOwn = true),
                ArtistAlbumRef(5, isOwn = false),
            )

        assertEquals(listOf(3L, 1L, 4L, 2L, 5L), artistAlbums(albums, refs).map { it.id })
    }

    @Test
    fun `albums of the same year are ordered by title`() {
        val albums = listOf(album(1, "beta", 2020), album(2, "Alpha", 2020), album(3, "alpha", 2020))
        val refs = albums.map { ArtistAlbumRef(it.id, isOwn = true) }

        assertEquals(listOf(2L, 3L, 1L), artistAlbums(albums, refs).map { it.id })
    }

    @Test
    fun `refs to albums without present tracks are dropped`() {
        assertEquals(emptyList<Album>(), artistAlbums(listOf(album(1, "X", 2020)), listOf(ArtistAlbumRef(2, true))))
        assertEquals(emptyList<Album>(), artistAlbums(emptyList(), emptyList()))
    }
}
