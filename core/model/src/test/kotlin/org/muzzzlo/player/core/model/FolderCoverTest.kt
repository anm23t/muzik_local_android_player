package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FolderCoverTest {
    @Test
    fun `cover names are matched by name and extension ignoring case`() {
        val cases =
            listOf(
                "cover.jpg" to 0,
                "Cover.JPG" to 0,
                "COVER.jpeg" to 0,
                "cover.png" to 0,
                "folder.jpg" to 1,
                "Front.Png" to 2,
                "album.jpeg" to 3,
                "cover_small.jpg" to null,
                "cover.jpg.bak" to null,
                "cover.gif" to null,
                "cover.webp" to null,
                "my cover.jpg" to null,
                "albumart.jpg" to null,
                "AlbumArtSmall.jpg" to null,
                "cover" to null,
                ".jpg" to null,
                "cover." to null,
            )
        for ((name, rank) in cases) assertEquals(rank, FolderCover.rank(name), name)
    }

    @Test
    fun `the first name of the list wins`() {
        assertEquals("cover.png", FolderCover.pick(listOf("album.jpg", "front.jpg", "folder.jpg", "cover.png")))
        assertEquals("Folder.JPG", FolderCover.pick(listOf("album.jpg", "Folder.JPG", "front.jpg")))
        assertEquals("album.jpeg", FolderCover.pick(listOf("track01.jpg", "album.jpeg")))
        assertNull(FolderCover.pick(listOf("scan1.jpg", "cover_small.jpg")))
        assertNull(FolderCover.pick(emptyList()))
    }

    @Test
    fun `equal names are settled without depending on order`() {
        assertEquals("cover.jpeg", FolderCover.pick(listOf("cover.png", "cover.jpeg", "cover.jpg")))
        assertEquals("cover.jpeg", FolderCover.pick(listOf("cover.jpg", "cover.png", "cover.jpeg")))
    }

    @Test
    fun `disc folders`() {
        val cases =
            listOf(
                "CD1" to true,
                "cd 2" to true,
                "CD01" to true,
                "Disc 2" to true,
                "disc2" to true,
                "DISK_03" to true,
                "Disc-1" to true,
                "Disc.1" to true,
                "CD 1 - Bonus" to true,
                "CD1 (Live)" to true,
                "CDs" to false,
                "CD" to false,
                "Disc" to false,
                "Discography" to false,
                "CD1a" to false,
                "CD123" to false,
                "My CD1" to false,
                "Album" to false,
            )
        for ((name, disc) in cases) assertEquals(disc, FolderCover.isDiscFolder(name), name)
    }

    @Test
    fun `an album in one folder looks only there`() {
        val tracks = listOf(place("Music/A/Album/", 1, 1), place("Music/A/Album/", 1, 2))
        assertEquals(listOf("Music/A/Album/"), FolderCover.albumFolders(tracks))
    }

    @Test
    fun `an album in disc folders also looks in the parent`() {
        val tracks = listOf(place("Music/A/Album/CD2/", 2, 1), place("Music/A/Album/CD1/", 1, 1))
        assertEquals(listOf("Music/A/Album/CD1/", "Music/A/Album/"), FolderCover.albumFolders(tracks))
    }

    @Test
    fun `a disc folder at the root has the root as its parent`() {
        assertEquals(listOf("CD1/", ""), FolderCover.albumFolders(listOf(place("CD1/", 1, 1))))
    }

    @Test
    fun `an album in different folders takes the folder of the first track by disc and number`() {
        val tracks =
            listOf(
                place("Music/Second/", 1, 5),
                place("Music/Third/", 2, 1),
                place("Music/First/", 1, 1),
            )
        assertEquals(listOf("Music/First/"), FolderCover.albumFolders(tracks))
    }

    @Test
    fun `tracks without numbers come after numbered ones`() {
        val tracks = listOf(place("Music/NoNumber/", null, null), place("Music/Numbered/", null, 3))
        assertEquals(listOf("Music/Numbered/"), FolderCover.albumFolders(tracks))
        assertEquals(emptyList<String>(), FolderCover.albumFolders(emptyList()))
    }

    @Test
    fun `the cover of the own folder wins over the parent`() {
        val tracks = listOf(place("Music/Album/CD1/", 1, 1))
        val pictures =
            mapOf(
                "Music/Album/CD1/" to listOf(picture("Music/Album/CD1/", "front.jpg")),
                "Music/Album/" to listOf(picture("Music/Album/", "cover.jpg")),
            )
        assertEquals(picture("Music/Album/CD1/", "front.jpg"), FolderCover.coverOf(tracks, pictures))
    }

    @Test
    fun `the parent is used when the disc folder has no cover`() {
        val tracks = listOf(place("Music/Album/Disc 1/", 1, 1))
        val pictures =
            mapOf(
                "Music/Album/Disc 1/" to listOf(picture("Music/Album/Disc 1/", "scan.jpg")),
                "Music/Album/" to listOf(picture("Music/Album/", "Folder.jpg")),
            )
        assertEquals(picture("Music/Album/", "Folder.jpg"), FolderCover.coverOf(tracks, pictures))
    }

    @Test
    fun `no cover anywhere gives none`() {
        val tracks = listOf(place("Music/Album/", 1, 1))
        val pictures = mapOf("Music/Other/" to listOf(picture("Music/Other/", "cover.jpg")))
        assertNull(FolderCover.coverOf(tracks, pictures))
    }

    private fun picture(
        path: String,
        name: String,
    ) = FolderPicture(path, name, "content://media/external/images/media/${path.hashCode()}$name", 1)

    private fun place(
        path: String,
        disc: Int?,
        number: Int?,
    ) = AlbumTrackPlace(path, disc, number)
}
