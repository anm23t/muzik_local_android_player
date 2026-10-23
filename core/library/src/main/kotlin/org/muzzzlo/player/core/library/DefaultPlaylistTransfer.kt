package org.muzzzlo.player.core.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.LibraryFile
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.M3u
import org.muzzzlo.player.core.model.M3uItem
import org.muzzzlo.player.core.model.M3uMatcher
import org.muzzzlo.player.core.model.PlaylistImport
import org.muzzzlo.player.core.model.PlaylistRepository
import org.muzzzlo.player.core.model.PlaylistTransfer
import org.muzzzlo.player.core.model.Track
import java.io.IOException
import javax.inject.Inject

/**
 * [PlaylistTransfer] over the content resolver. The database keeps no file names, so the paths of the library
 * files are asked from MediaStore at the moment of an import or export.
 */
internal class DefaultPlaylistTransfer
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val library: LibraryRepository,
        private val playlists: PlaylistRepository,
        private val dispatchers: DispatcherProvider,
    ) : PlaylistTransfer {
        override suspend fun import(
            uri: String,
            fallbackName: String,
        ): PlaylistImport =
            withContext(dispatchers.io) {
                val document = Uri.parse(uri)
                val bytes =
                    context.contentResolver.openInputStream(document)?.use { it.readBytes() }
                        ?: throw IOException("Cannot open $uri")
                val entries = M3u.parse(M3u.decode(bytes))
                val tracks = library.observeTracks().first()
                val paths = paths()
                val files =
                    tracks.mapNotNull { track -> paths[track.uri]?.let { LibraryFile(track.id, it, track.durationMs) } }
                val match = M3uMatcher.match(entries, files)
                val name = M3u.playlistName(displayName(document), fallbackName)
                val id = if (match.trackIds.isEmpty()) null else playlists.createWithTracks(name, match.trackIds)
                PlaylistImport(id, name, match.trackIds.size, match.unmatched)
            }

        override suspend fun export(
            playlistId: Long,
            uri: String,
        ) {
            withContext(dispatchers.io) {
                val tracks = playlists.observeEntries(playlistId).first().map { it.track }
                val paths = paths()
                val items = tracks.mapNotNull { track -> paths[track.uri]?.let { item(track, it) } }
                val stream =
                    context.contentResolver.openOutputStream(Uri.parse(uri), "wt")
                        ?: throw IOException("Cannot open $uri")
                stream.use { it.write(M3u.encode(items).toByteArray(Charsets.UTF_8)) }
            }
        }

        private fun item(
            track: Track,
            path: String,
        ) = M3uItem(path, track.durationMs, track.artists, track.title)

        /**
         * Absolute path of every audio file by its `content://` URI. `DATA` is deprecated since Android 10 but still
         * filled for reading; without it the path is built from `RELATIVE_PATH` on the primary storage.
         */
        @Suppress("DEPRECATION")
        private fun paths(): Map<String, String> {
            val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val hasRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            val projection =
                buildList {
                    add(MediaStore.Audio.Media._ID)
                    add(MediaStore.Audio.Media.DISPLAY_NAME)
                    add(MediaStore.Audio.Media.DATA)
                    if (hasRelativePath) add(MediaStore.Audio.Media.RELATIVE_PATH)
                }.toTypedArray()
            val root = Environment.getExternalStorageDirectory().path
            val result = HashMap<String, String>()
            context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val pathCol =
                    if (hasRelativePath) {
                        cursor.getColumnIndexOrThrow(
                            MediaStore.Audio.Media.RELATIVE_PATH,
                        )
                    } else {
                        -1
                    }
                while (cursor.moveToNext()) {
                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol)).toString()
                    val data = cursor.getString(dataCol)
                    val relative = if (pathCol >= 0) cursor.getString(pathCol) else null
                    val name = cursor.getString(nameCol)
                    val path =
                        when {
                            !data.isNullOrEmpty() -> data
                            relative != null && name != null -> "$root/$relative$name"
                            else -> null
                        }
                    if (path != null) result[uri] = path
                }
            }
            return result
        }

        private fun displayName(document: Uri): String? =
            context.contentResolver.query(document, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
    }
