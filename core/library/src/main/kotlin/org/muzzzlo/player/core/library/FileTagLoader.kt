package org.muzzzlo.player.core.library

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import org.muzzzlo.player.core.model.EmbeddedPicture
import org.muzzzlo.player.core.model.TagReadResult
import org.muzzzlo.player.core.model.TagReader
import org.muzzzlo.player.core.model.TagSource
import java.io.FileNotFoundException
import javax.inject.Inject

/**
 * Reads the tags of a MediaStore file through its `content://` URI: the file is opened read-only and handed to
 * [TagReader] as a descriptor, so nothing is copied and no file path is needed under scoped storage.
 */
class FileTagLoader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val tagReader: TagReader,
    ) {
        /**
         * Tags of the file at [uri]; [TagReadResult.Unreadable] when it cannot be opened. [fileName] (MediaStore
         * `DISPLAY_NAME`) lets the reader pick the format by extension. Call off the main thread.
         */
        suspend fun load(
            uri: String,
            fileName: String?,
        ): TagReadResult = withDescriptor(uri, fileName) { tagReader.readTags(it) } ?: TagReadResult.Unreadable

        /** The cover of the file at [uri], null when it has none or cannot be opened. Call off the main thread. */
        suspend fun loadCover(
            uri: String,
            fileName: String?,
        ): EmbeddedPicture? = withDescriptor(uri, fileName) { tagReader.readCover(it) }

        /** Opens [uri] read-only for [block]; null when it cannot be opened. */
        @Suppress("SwallowedException")
        private suspend fun <T> withDescriptor(
            uri: String,
            fileName: String?,
            block: suspend (TagSource) -> T,
        ): T? {
            // A file deleted since the MediaStore query, or one the app may no longer read, is indexed from MediaStore.
            val descriptor =
                try {
                    context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
                } catch (e: FileNotFoundException) {
                    null
                } catch (e: SecurityException) {
                    null
                } ?: return null
            return descriptor.use { block(TagSource.Descriptor(it.fd, key = uri, fileName = fileName)) }
        }
    }
