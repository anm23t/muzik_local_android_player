package org.muzzzlo.player.core.tags

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import org.muzzzlo.player.core.model.TagWriteTarget
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * [OriginalFiles] for MediaStore files under scoped storage; path targets go to [PathOriginalFiles].
 *
 * The original is overwritten through `openFileDescriptor(uri, "rwt")`: a file cannot be renamed over a
 * MediaStore file, so the replacement is not atomic and is protected by the backup and the journal.
 */
internal class MediaStoreOriginalFiles(
    private val context: Context,
    private val paths: PathOriginalFiles = PathOriginalFiles(),
) : OriginalFiles {
    private val resolver: ContentResolver get() = context.contentResolver

    @Suppress("SwallowedException")
    override fun find(target: TagWriteTarget): OriginalFile? =
        when (target) {
            is TagWriteTarget.FilePath -> paths.find(target)
            is TagWriteTarget.ContentUri -> {
                val uri = Uri.parse(target.uri)
                try {
                    resolver.openFileDescriptor(uri, "r")?.use { ContentUriOriginalFile(resolver, uri) }
                } catch (e: FileNotFoundException) {
                    null
                }
            }
        }

    /**
     * Android 8–9 grant writing to shared storage with `WRITE_EXTERNAL_STORAGE`. Android 10+ grant it per file
     * (`MediaStore.createWriteRequest`, `RecoverableSecurityException`), so the file is opened for writing without
     * truncating it and closed again; a refusal is a [SecurityException].
     */
    @Suppress("SwallowedException")
    override fun canWrite(target: TagWriteTarget): Boolean =
        when (target) {
            is TagWriteTarget.FilePath -> paths.canWrite(target)
            is TagWriteTarget.ContentUri -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !hasLegacyWritePermission()) {
                    false
                } else {
                    try {
                        val descriptor = resolver.openFileDescriptor(Uri.parse(target.uri), "rw")
                        descriptor?.close()
                        descriptor != null
                    } catch (e: SecurityException) {
                        false
                    } catch (e: FileNotFoundException) {
                        false
                    }
                }
            }
        }

    private fun hasLegacyWritePermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
}

private class ContentUriOriginalFile(
    private val resolver: ContentResolver,
    private val uri: Uri,
) : OriginalFile {
    override fun size(): Long =
        resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: throw FileNotFoundException(uri.toString())

    override fun openInput(): InputStream = resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())

    override fun overwriteWith(source: File) {
        val descriptor = resolver.openFileDescriptor(uri, "rwt") ?: throw FileNotFoundException(uri.toString())
        descriptor.use {
            FileOutputStream(it.fileDescriptor).use { output ->
                copyAndSync(source, output) {
                    // Some providers ignore the "t" of "rwt"; cut a longer old content explicitly.
                    output.channel.truncate(source.length())
                    it.fileDescriptor.sync()
                }
            }
        }
        if (size() != source.length()) throw IOException("Size of $uri differs after the write")
    }
}
