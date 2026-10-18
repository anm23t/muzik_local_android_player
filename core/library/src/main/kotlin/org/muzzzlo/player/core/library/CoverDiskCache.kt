package org.muzzzlo.player.core.library

import java.io.File
import java.security.MessageDigest

/**
 * Folder of small cover files with a size limit. Android-free, so the rules are unit-tested on the JVM.
 *
 * A hit refreshes the file's modification time; when the folder grows over [limitBytes], the files that were
 * used the longest ago are deleted first. The newest file is always kept, even if it alone exceeds the limit.
 * Everything in the folder can be made again from the audio files (ARCHITECTURE.md, section 2).
 *
 * @param clock current time in milliseconds, replaced in tests.
 */
internal class CoverDiskCache(
    private val folder: File,
    private val limitBytes: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** The cached file for [key], or null. Counts as a use. */
    fun get(key: String): File? {
        val file = fileFor(key)
        if (!file.isFile) return null
        file.setLastModified(clock())
        return file
    }

    /** Stores [bytes] under [key], then trims the folder to the limit. Returns the stored file. */
    fun put(
        key: String,
        bytes: ByteArray,
    ): File {
        folder.mkdirs()
        val file = fileFor(key)
        // A half-written file must never be taken for a cover, so it is renamed only when complete.
        val partial = File(folder, "${file.name}$PART")
        partial.writeBytes(bytes)
        check(partial.renameTo(file)) { "Cannot store the cover" }
        file.setLastModified(clock())
        trim(keep = file)
        return file
    }

    /** Deletes the files used the longest ago until the folder fits the limit. */
    private fun trim(keep: File) {
        val files = folder.listFiles { f -> f.isFile && f.name.endsWith(EXTENSION) }.orEmpty()
        var total = files.sumOf { it.length() }
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= limitBytes) return
            if (file == keep) continue
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    private fun fileFor(key: String) = File(folder, "$key$EXTENSION")

    private companion object {
        const val EXTENSION = ".jpg"
        const val PART = ".part"
    }
}

/**
 * Cache key of a track's cover: it follows the file's address, size and change time, so a rewritten file
 * (tag editor, another app) gets a new key and the old cover is never shown for it. The hash keeps the name
 * short and safe for a file system.
 */
internal fun coverKey(
    uri: String,
    dateModified: Long?,
    fileSize: Long?,
): String {
    val digest = MessageDigest.getInstance("SHA-1").digest("$uri|$dateModified|$fileSize".toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
