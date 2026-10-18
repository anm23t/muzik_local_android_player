package org.muzzzlo.player.core.tags

import org.muzzzlo.player.core.model.TagWriteTarget
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest

/**
 * The user's file whose tags are written, as the safe write sees it: it is only read whole and overwritten whole.
 * The tag library never opens it (ARCHITECTURE.md, section 5).
 */
internal interface OriginalFile {
    /** Size in bytes. */
    fun size(): Long

    /** Opens the file for reading from the start. */
    fun openInput(): InputStream

    /**
     * Replaces the whole content with the bytes of [source] and flushes it to storage.
     * Not atomic: a failure midway leaves the file damaged, and the caller restores it from a backup.
     */
    fun overwriteWith(source: File)
}

/** Finds the [OriginalFile] of a [TagWriteTarget] and tells whether the app may change it. */
internal interface OriginalFiles {
    /** The file, or null when it does not exist or cannot be opened. */
    fun find(target: TagWriteTarget): OriginalFile?

    /** True when the app may overwrite the file now, without asking the user. */
    fun canWrite(target: TagWriteTarget): Boolean
}

/** [OriginalFiles] for [TagWriteTarget.FilePath] targets, the app's own files and JVM tests. */
internal class PathOriginalFiles : OriginalFiles {
    override fun find(target: TagWriteTarget): OriginalFile? {
        val file = File((target as? TagWriteTarget.FilePath)?.path ?: return null)
        return if (file.isFile) PathOriginalFile(file) else null
    }

    override fun canWrite(target: TagWriteTarget): Boolean =
        target is TagWriteTarget.FilePath && File(target.path).canWrite()
}

private class PathOriginalFile(private val file: File) : OriginalFile {
    override fun size(): Long = file.length()

    override fun openInput(): InputStream = FileInputStream(file)

    override fun overwriteWith(source: File) {
        FileOutputStream(file).use { output -> copyAndSync(source, output) { output.fd.sync() } }
    }
}

/** Copies [source] into [output], then calls [sync] to push the bytes to storage. */
internal fun copyAndSync(
    source: File,
    output: OutputStream,
    sync: () -> Unit,
) {
    FileInputStream(source).use { it.copyTo(output) }
    output.flush()
    sync()
}

/** SHA-256 of everything [input] gives; closes it. */
internal fun sha256(input: InputStream): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    input.use { stream ->
        val buffer = ByteArray(COPY_BUFFER)
        var read = stream.read(buffer)
        while (read >= 0) {
            digest.update(buffer, 0, read)
            read = stream.read(buffer)
        }
    }
    return digest.digest()
}

/** Copies [input] into [destination] (synced to storage) and returns the SHA-256 of the bytes; closes [input]. */
internal fun copyWithSha256(
    input: InputStream,
    destination: File,
): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    DigestInputStream(input, digest).use { stream ->
        FileOutputStream(destination).use { output ->
            stream.copyTo(output, COPY_BUFFER)
            output.fd.sync()
        }
    }
    return digest.digest()
}

private const val COPY_BUFFER = 64 * 1024
