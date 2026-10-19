package org.muzzzlo.player.core.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import org.muzzzlo.player.core.model.LibraryFolder
import javax.inject.Inject

/** Reads the list of music files from MediaStore. */
class MediaStoreSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /** Every music file of the device. */
        fun query(): List<ScannedFile> = query(extraSelection = null, args = null)

        /** Every folder with music (and the root, as an empty path) with its track count; filters of the settings do not apply. */
        fun folders(): List<LibraryFolder> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
            val counts = HashMap<String, Int>()
            val projection = arrayOf(MediaStore.Audio.Media.RELATIVE_PATH, MediaStore.Audio.Media.DISPLAY_NAME)
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                musicSelection(null),
                null,
                null,
            )
                ?.use { cursor ->
                    while (cursor.moveToNext()) {
                        if (isAudiobookFile(cursor.getString(1))) continue
                        counts.merge(cursor.getString(0).orEmpty(), 1, Int::plus)
                    }
                }
            return counts.map { LibraryFolder(it.key, it.value) }.sortedBy { it.relativePath.lowercase() }
        }

        /**
         * The music file at [uri] (a `content://` URI of this collection), null when MediaStore no longer has it.
         * Call off the main thread.
         */
        fun file(uri: String): ScannedFile? {
            val id = runCatching { ContentUris.parseId(Uri.parse(uri)) }.getOrNull()?.takeIf { it >= 0 } ?: return null
            return query("${MediaStore.Audio.Media._ID} = ?", arrayOf(id.toString())).firstOrNull()
        }

        private fun query(
            extraSelection: String?,
            args: Array<String>?,
        ): List<ScannedFile> {
            val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val hasRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            val projection =
                buildList {
                    add(MediaStore.Audio.Media._ID)
                    add(MediaStore.Audio.Media.TITLE)
                    add(MediaStore.Audio.Media.DISPLAY_NAME)
                    add(MediaStore.Audio.Media.ARTIST)
                    add(MediaStore.Audio.Media.ALBUM)
                    add(MediaStore.Audio.Media.YEAR)
                    add(MediaStore.Audio.Media.TRACK)
                    add(MediaStore.Audio.Media.DURATION)
                    add(MediaStore.Audio.Media.MIME_TYPE)
                    add(MediaStore.Audio.Media.SIZE)
                    add(MediaStore.Audio.Media.DATE_ADDED)
                    add(MediaStore.Audio.Media.DATE_MODIFIED)
                    if (hasRelativePath) add(MediaStore.Audio.Media.RELATIVE_PATH)
                }.toTypedArray()
            val selection = musicSelection(extraSelection)

            val result = ArrayList<ScannedFile>()
            context.contentResolver.query(collection, projection, selection, args, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val pathCol =
                    if (hasRelativePath) {
                        cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
                    } else {
                        -1
                    }
                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(nameCol)
                    if (isAudiobookFile(displayName)) continue
                    val id = cursor.getLong(idCol)
                    val rawTrack = cursor.getInt(trackCol)
                    result +=
                        ScannedFile(
                            mediaStoreId = id,
                            uri = ContentUris.withAppendedId(collection, id).toString(),
                            relativePath = if (pathCol >= 0) cursor.getString(pathCol) else null,
                            displayName = displayName,
                            title = cursor.getString(titleCol).orEmpty(),
                            artist = cursor.getString(artistCol),
                            album = cursor.getString(albumCol),
                            year = cursor.getInt(yearCol).takeIf { it > 0 },
                            // MediaStore packs disc into thousands: 2005 = disc 2, track 5.
                            trackNumber = (rawTrack % 1000).takeIf { it > 0 },
                            discNumber = (rawTrack / 1000).takeIf { it > 0 },
                            durationMs = cursor.getLong(durationCol),
                            mimeType = cursor.getString(mimeCol),
                            fileSize = cursor.getLong(sizeCol),
                            dateAdded = cursor.getLong(addedCol),
                            dateModified = cursor.getLong(modifiedCol),
                        )
                }
            }
            return result
        }

        /** Podcasts and audiobooks never enter the library (ARCHITECTURE.md, section 3). */
        private fun musicSelection(extraSelection: String?): String =
            buildList {
                add("${MediaStore.Audio.Media.IS_MUSIC} != 0")
                add("${MediaStore.Audio.Media.IS_PODCAST} = 0")
                add("${MediaStore.Audio.Media.DURATION} > 0")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add("${MediaStore.Audio.Media.IS_AUDIOBOOK} = 0")
                if (extraSelection != null) add(extraSelection)
            }.joinToString(" AND ")
    }
