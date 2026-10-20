package org.muzzzlo.player.feature.settings

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.model.DispatcherProvider
import java.io.IOException
import javax.inject.Inject

/** Reads and writes the text of a document (the `content://` URI as text) that the user chose with the picker. */
interface BackupFiles {
    /** Throws [IOException] when the document cannot be opened. */
    suspend fun read(uri: String): String

    /** Replaces the whole content of the document; throws [IOException] when it cannot be written. */
    suspend fun write(
        uri: String,
        text: String,
    )
}

internal class ContentResolverBackupFiles
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dispatchers: DispatcherProvider,
    ) : BackupFiles {
        override suspend fun read(uri: String): String =
            withContext(dispatchers.io) {
                val stream =
                    context.contentResolver.openInputStream(Uri.parse(uri)) ?: throw IOException("Cannot open $uri")
                stream.use { it.readBytes().toString(Charsets.UTF_8) }
            }

        override suspend fun write(
            uri: String,
            text: String,
        ) {
            withContext(dispatchers.io) {
                val stream =
                    context.contentResolver.openOutputStream(Uri.parse(uri), "wt")
                        ?: throw IOException("Cannot open $uri")
                stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            }
        }
    }
