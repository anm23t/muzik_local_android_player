package org.muzzzlo.player.core.tags

import org.muzzzlo.player.core.model.TagWriteTarget
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Properties
import java.util.UUID

/**
 * Working copies, backups and the journal of the safe tag write (ARCHITECTURE.md, section 5).
 *
 * Every write gets its own folder in [directory] with a working copy and, once the original is about to be
 * replaced, a backup and a journal entry. The entry exists exactly while the original may be half-written:
 * it is written (and synced) after the backup and before the first byte goes to the original, and deleted
 * once the original was read back and matched. A folder without an entry is a leftover of a write that never
 * touched the original and is simply deleted.
 *
 * [recover] finishes an interrupted replacement after a crash or a power loss: the original is left alone when it
 * already has the old or the new content and is restored from the backup otherwise. A folder whose original
 * cannot be restored yet (the app lost the permission to change it) stays and is retried later.
 *
 * All methods do file I/O: call them off the main thread.
 */
internal class WriteJournal(val directory: File) {
    /** The folder of one write. */
    inner class Transaction internal constructor(val folder: File) {
        private val journalFile = File(folder, JOURNAL_FILE)

        /** The working copy; keeps the extension of [fileName], so the tag library picks the format by it. */
        fun workingCopy(fileName: String): File = File(folder, "work${extensionOf(fileName)}")

        /** The backup of the original. */
        fun backup(fileName: String): File = File(folder, "backup${extensionOf(fileName)}")

        /** True once [begin] recorded the replacement. */
        fun hasEntry(): Boolean = journalFile.isFile

        /** The journal entry, or null when the original was never about to be replaced. */
        @Suppress("SwallowedException")
        fun entry(): Entry? =
            try {
                if (journalFile.isFile) {
                    Entry.fromProperties(Properties().apply { journalFile.reader().use(::load) })
                } else {
                    null
                }
            } catch (e: IllegalArgumentException) {
                null
            }

        /** Records that the original is about to be replaced; synced to storage before returning. */
        fun begin(entry: Entry) {
            val temporary = File(folder, "$JOURNAL_FILE.tmp")
            FileOutputStream(temporary).use { output ->
                entry.toProperties().store(output, null)
                output.fd.sync()
            }
            if (!temporary.renameTo(journalFile)) throw IOException("Cannot write ${journalFile.path}")
        }

        /** Deletes the entry first and the copies after it, so a crash midway leaves only a harmless leftover. */
        fun delete() {
            journalFile.delete()
            folder.deleteRecursively()
        }
    }

    /**
     * What is needed to finish an interrupted replacement.
     *
     * @property target the original file.
     * @property oldSha256 hash of the original before the write; the backup has the same content.
     * @property newSha256 hash of the checked working copy written over the original.
     */
    data class Entry(
        val target: TagWriteTarget,
        val oldSha256: ByteArray,
        val newSha256: ByteArray,
    ) {
        fun toProperties(): Properties =
            Properties().apply {
                when (target) {
                    is TagWriteTarget.FilePath -> setProperty(PATH, target.path)
                    is TagWriteTarget.ContentUri -> {
                        setProperty(URI, target.uri)
                        setProperty(NAME, target.fileName)
                    }
                }
                setProperty(OLD_SHA, oldSha256.toHex())
                setProperty(NEW_SHA, newSha256.toHex())
            }

        override fun equals(other: Any?): Boolean =
            other is Entry && target == other.target &&
                oldSha256.contentEquals(other.oldSha256) && newSha256.contentEquals(other.newSha256)

        override fun hashCode(): Int = target.hashCode()

        companion object {
            private const val PATH = "path"
            private const val URI = "uri"
            private const val NAME = "fileName"
            private const val OLD_SHA = "oldSha256"
            private const val NEW_SHA = "newSha256"

            fun fromProperties(properties: Properties): Entry {
                val path = properties.getProperty(PATH)
                val uri = properties.getProperty(URI)
                val target =
                    when {
                        path != null -> TagWriteTarget.FilePath(path)
                        uri != null -> TagWriteTarget.ContentUri(uri, properties.getProperty(NAME).orEmpty())
                        else -> throw IllegalArgumentException("Journal entry without a file")
                    }
                return Entry(
                    target = target,
                    oldSha256 = requireNotNull(properties.getProperty(OLD_SHA)).fromHex(),
                    newSha256 = requireNotNull(properties.getProperty(NEW_SHA)).fromHex(),
                )
            }
        }
    }

    /** Starts the folder of a new write. */
    fun newTransaction(): Transaction {
        val folder = File(directory, UUID.randomUUID().toString())
        if (!folder.mkdirs()) throw IOException("Cannot create ${folder.path}")
        return Transaction(folder)
    }

    /** Folders of all writes left from earlier, oldest first. */
    fun transactions(): List<Transaction> =
        directory.listFiles().orEmpty()
            .filter { it.isDirectory }
            .sortedBy { it.lastModified() }
            .map { Transaction(it) }

    /**
     * Finishes [transaction]: deletes it when the original is intact (old or new content) or was restored from the
     * backup. Returns false when the original still needs restoring and the folder is kept for a later attempt.
     */
    @Suppress("SwallowedException")
    fun recover(
        transaction: Transaction,
        files: OriginalFiles,
    ): Boolean {
        val entry = transaction.entry()
        if (entry == null) {
            transaction.delete()
            return true
        }
        val restored =
            try {
                val original = files.find(entry.target)
                original != null &&
                    restoreIfNeeded(original, transaction.backup(entry.target.fileName), entry, acceptNew = true)
            } catch (e: IOException) {
                false
            } catch (e: SecurityException) {
                false
            }
        if (restored) transaction.delete()
        return restored
    }

    /** Recovers every folder left from earlier; returns the targets whose originals still need restoring. */
    fun recoverAll(files: OriginalFiles): List<TagWriteTarget> =
        transactions().mapNotNull { transaction ->
            val target = transaction.entry()?.target
            if (recover(transaction, files)) null else target
        }

    private fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('.', missingDelimiterValue = "").let { if (it.isEmpty()) "" else ".$it" }

    companion object {
        private const val JOURNAL_FILE = "journal.properties"

        /**
         * Brings [original] back to the content of [backup] unless it already has it (or, with [acceptNew], the new
         * content of a replacement that did finish). True when the original ends up intact.
         */
        fun restoreIfNeeded(
            original: OriginalFile,
            backup: File,
            entry: Entry,
            acceptNew: Boolean,
        ): Boolean {
            val current = sha256(original.openInput())
            val intact = current.contentEquals(entry.oldSha256) || acceptNew && current.contentEquals(entry.newSha256)
            if (intact) return true
            if (!backup.isFile) return false
            original.overwriteWith(backup)
            return sha256(original.openInput()).contentEquals(entry.oldSha256)
        }
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun String.fromHex(): ByteArray {
    require(length % 2 == 0) { "Bad hex string" }
    return chunked(2).map { it.toInt(radix = 16).toByte() }.toByteArray()
}
