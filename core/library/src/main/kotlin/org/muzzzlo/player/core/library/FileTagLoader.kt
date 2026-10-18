package org.muzzzlo.player.core.library

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
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
        @Suppress("SwallowedException")
        suspend fun load(
            uri: String,
            fileName: String?,
        ): TagReadResult {
            // A file deleted since the MediaStore query, or one the app may no longer read, is indexed from MediaStore.
            val descriptor =
                try {
                    context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
                } catch (e: FileNotFoundException) {
                    null
                } catch (e: SecurityException) {
                    null
                } ?: return TagReadResult.Unreadable
            return descriptor.use { tagReader.readTags(TagSource.Descriptor(it.fd, key = uri, fileName = fileName)) }
        }
    }
