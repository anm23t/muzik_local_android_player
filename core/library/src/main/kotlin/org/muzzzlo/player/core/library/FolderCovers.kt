package org.muzzzlo.player.core.library

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import org.muzzzlo.player.core.database.dao.AlbumFolderCover
import org.muzzzlo.player.core.database.dao.AlbumTrackFolder
import org.muzzzlo.player.core.database.dao.LibraryDao
import org.muzzzlo.player.core.model.AlbumTrackPlace
import org.muzzzlo.player.core.model.FolderCover
import org.muzzzlo.player.core.model.FolderPicture
import javax.inject.Inject

/** A new folder cover of an album; both null when the album stops using one. */
internal data class FolderCoverChange(
    val albumId: Long,
    val uri: String?,
    val modified: Long?,
)

/**
 * The albums whose folder cover must change. [pictures] are the cover-like pictures of the device, null when folder
 * covers are off (or cannot be read), which clears every folder cover. An album that got a cover in its tags, or lost
 * its tracks, is not in [tracks] and loses its folder cover too.
 */
internal fun folderCoverChanges(
    tracks: List<AlbumTrackFolder>,
    current: List<AlbumFolderCover>,
    pictures: List<FolderPicture>?,
): List<FolderCoverChange> {
    val byFolder = pictures.orEmpty().groupBy { it.relativePath }
    val places = tracks.groupBy({ it.albumId }, { AlbumTrackPlace(it.relativePath, it.discNumber, it.trackNumber) })
    return current.mapNotNull { album ->
        val picture = if (pictures == null) null else places[album.id]?.let { FolderCover.coverOf(it, byFolder) }
        val change = FolderCoverChange(album.id, picture?.uri, picture?.modified)
        change.takeIf { it.uri != album.uri || it.modified != album.modified }
    }
}

/**
 * Finds the cover pictures lying next to the tracks (task 2.0.5) and keeps `album.folder_cover_*` up to date after
 * each scan. A changed cover drops the accent of its album; its thumbnail gets a new cache key by itself.
 * Call off the main thread.
 */
internal class FolderCoverIndexer
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dao: LibraryDao,
    ) {
        /** Brings the folder covers in line with the files; [enabled] is the setting of the user. */
        suspend fun refresh(enabled: Boolean) {
            val pictures = if (enabled && canReadPictures()) coverPictures() else null
            val changes = folderCoverChanges(dao.tracksOfAlbumsWithoutCover(), dao.albumFolderCovers(), pictures)
            changes.forEach { dao.setAlbumFolderCover(it.albumId, it.uri, it.modified) }
        }

        /** Folders exist in MediaStore from Android 10; images need their own permission from Android 13. */
        private fun canReadPictures(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
            val permission =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
            return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }

        /** Every picture of the device named like a cover, in one query. */
        private fun coverPictures(): List<FolderPicture> {
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection =
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.RELATIVE_PATH,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_MODIFIED,
                )
            val names = FolderCover.CANDIDATE_NAMES
            val selection = "LOWER(${MediaStore.Images.Media.DISPLAY_NAME}) IN (${names.joinToString { "?" }})"
            val result = ArrayList<FolderPicture>()
            context.contentResolver.query(collection, projection, selection, names.toTypedArray(), null)?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val pathCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val modifiedCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                while (it.moveToNext()) {
                    val path = it.getString(pathCol)
                    val name = it.getString(nameCol)
                    if (path == null || name == null) continue
                    val uri = ContentUris.withAppendedId(collection, it.getLong(idCol)).toString()
                    result += FolderPicture(path, name, uri, it.getLong(modifiedCol))
                }
            }
            return result
        }
    }
