package org.muzzzlo.player.core.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.database.dao.AlbumFolderCover
import org.muzzzlo.player.core.database.dao.AlbumTrackFolder
import org.muzzzlo.player.core.model.FolderPicture

class FolderCoverChangesTest {
    private val coverJpg = FolderPicture("Music/A/", "cover.jpg", "content://images/1", 10)

    @Test
    fun `a new cover is set`() {
        val changes = folderCoverChanges(listOf(track(1, "Music/A/")), listOf(none(1)), listOf(coverJpg))
        assertEquals(listOf(FolderCoverChange(1, "content://images/1", 10)), changes)
    }

    @Test
    fun `an unchanged cover is left alone, so its accent stays`() {
        val current = AlbumFolderCover(1, "content://images/1", 10)
        assertEquals(
            emptyList<FolderCoverChange>(),
            folderCoverChanges(listOf(track(1, "Music/A/")), listOf(current), listOf(coverJpg)),
        )
    }

    @Test
    fun `a replaced picture with the same uri is a change`() {
        val current = AlbumFolderCover(1, "content://images/1", 9)
        val changes = folderCoverChanges(listOf(track(1, "Music/A/")), listOf(current), listOf(coverJpg))
        assertEquals(listOf(FolderCoverChange(1, "content://images/1", 10)), changes)
    }

    @Test
    fun `a better named picture replaces the old one`() {
        val folderJpg = FolderPicture("Music/A/", "folder.jpg", "content://images/2", 5)
        val current = AlbumFolderCover(1, "content://images/2", 5)
        val changes = folderCoverChanges(listOf(track(1, "Music/A/")), listOf(current), listOf(folderJpg, coverJpg))
        assertEquals(listOf(FolderCoverChange(1, "content://images/1", 10)), changes)
    }

    @Test
    fun `a deleted picture clears the cover`() {
        val current = AlbumFolderCover(1, "content://images/1", 10)
        val changes = folderCoverChanges(listOf(track(1, "Music/A/")), listOf(current), emptyList())
        assertEquals(listOf(FolderCoverChange(1, null, null)), changes)
    }

    @Test
    fun `an album that got a cover in its tags loses the folder cover`() {
        // The album is not among the albums without a cover any more.
        val current = AlbumFolderCover(1, "content://images/1", 10)
        assertEquals(
            listOf(FolderCoverChange(1, null, null)),
            folderCoverChanges(emptyList(), listOf(current), listOf(coverJpg)),
        )
    }

    @Test
    fun `turning folder covers off clears every cover`() {
        val current = listOf(AlbumFolderCover(1, "content://images/1", 10), none(2))
        val changes = folderCoverChanges(listOf(track(1, "Music/A/")), current, null)
        assertEquals(listOf(FolderCoverChange(1, null, null)), changes)
    }

    @Test
    fun `an album in a disc folder takes the cover of the parent`() {
        val changes = folderCoverChanges(listOf(track(1, "Music/A/CD1/")), listOf(none(1)), listOf(coverJpg))
        assertEquals(listOf(FolderCoverChange(1, "content://images/1", 10)), changes)
    }

    private fun track(
        albumId: Long,
        path: String,
    ) = AlbumTrackFolder(albumId, path, 1, 1)

    private fun none(id: Long) = AlbumFolderCover(id, null, null)
}
