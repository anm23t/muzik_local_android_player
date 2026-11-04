package org.muzzzlo.player.core.library

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.LyricsRepository
import org.muzzzlo.player.core.model.SettingsRepository
import org.muzzzlo.player.core.model.TagReadResult
import org.muzzzlo.player.core.model.TrackLyrics
import org.muzzzlo.player.core.model.TrashRules
import org.muzzzlo.player.core.model.chooseLyrics
import java.io.File
import javax.inject.Inject

/**
 * [LyricsRepository] that reads the lyrics tag of the file and, when it is empty, the `.lrc` file of the same name
 * next to it (task 2.0.7). Nothing is cached: the tag can change in the editor, and one file is read per track.
 *
 * A `.lrc` file is not media, so Android 10 and newer let the app read it only inside a folder the user opened
 * through the system folder picker (setting "Lyrics files", [org.muzzzlo.player.core.model.AppSettings.lyricsFolder]);
 * Android 8–9 read it by its path with the storage permission the app already has.
 */
internal class DefaultLyricsRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val files: TrackFiles,
        private val settings: SettingsRepository,
        private val dispatchers: DispatcherProvider,
    ) : LyricsRepository {
        override suspend fun lyricsOf(trackId: Long): TrackLyrics? =
            withContext(dispatchers.io) {
                val file = files.find(trackId) ?: return@withContext null
                val tagText = (files.readTags(file) as? TagReadResult.Success)?.fileTags?.tags?.lyrics
                chooseLyrics(tagText) { readLrc(file) }
            }

        private suspend fun readLrc(file: ScannedFile): String? {
            val lrcName = file.displayName?.let(TrashRules::lyricsFileName) ?: return null
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return readByPath(file.uri, lrcName)
            val tree = settings.settings.first().lyricsFolder
            val folder = file.relativePath
            return if (tree != null && folder != null) readInTree(Uri.parse(tree), folder, lrcName) else null
        }

        @Suppress("DEPRECATION")
        private fun readByPath(
            audioUri: String,
            lrcName: String,
        ): String? {
            val path =
                runCatching {
                    context.contentResolver
                        .query(Uri.parse(audioUri), arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
                        ?.use { if (it.moveToFirst()) it.getString(0) else null }
                }.getOrNull() ?: return null
            val lrc = File(File(path).parentFile, lrcName)
            return runCatching { lrc.takeIf { it.isFile }?.readText() }.getOrNull()
        }

        /** The document of `<folder><name>` inside the opened [tree], read as text; null when it is not there. */
        private fun readInTree(
            tree: Uri,
            folder: String,
            lrcName: String,
        ): String? {
            val documentId = lrcDocumentId(DocumentsContract.getTreeDocumentId(tree), folder, lrcName) ?: return null
            val document = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)
            return runCatching {
                context.contentResolver.openInputStream(document)?.use { it.readBytes().toString(Charsets.UTF_8) }
            }.getOrNull()
        }
    }

/**
 * The document id of a `.lrc` file in a folder tree of the system storage provider. Ids look like
 * `primary:Music/Album/Song.lrc`: the volume, a colon and the path. Null when [folder] (a MediaStore `RELATIVE_PATH`
 * such as `Music/Album/`) lies outside the tree [treeDocumentId] (`primary:Music`), which the app may not read.
 */
internal fun lrcDocumentId(
    treeDocumentId: String,
    folder: String,
    lrcName: String,
): String? {
    val colon = treeDocumentId.indexOf(':')
    if (colon < 0) return null
    val volume = treeDocumentId.substring(0, colon)
    val treePath = treeDocumentId.substring(colon + 1).trim('/')
    val path = folder.trim('/')
    val inside =
        treePath.isEmpty() || path.equals(treePath, ignoreCase = true) ||
            path.startsWith("$treePath/", ignoreCase = true)
    if (!inside) return null
    val filePath = if (path.isEmpty()) lrcName else "$path/$lrcName"
    return "$volume:$filePath"
}
