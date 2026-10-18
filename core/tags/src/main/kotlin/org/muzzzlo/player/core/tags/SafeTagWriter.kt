package org.muzzzlo.player.core.tags

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.model.AudioFormat
import org.muzzzlo.player.core.model.CoverEdit
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.TagEdit
import org.muzzzlo.player.core.model.TagSource
import org.muzzzlo.player.core.model.TagWriteResult
import org.muzzzlo.player.core.model.TagWriteTarget
import org.muzzzlo.player.core.model.TagWriter
import java.io.File
import java.io.IOException

/**
 * [TagWriter] that never lets the tag library touch the user's file (ARCHITECTURE.md, section 5):
 *
 * 1. checks the permission and the free space for two copies;
 * 2. copies the original into a working copy in the app's private storage;
 * 3. writes the new tags into the working copy with TagLib, keeping every field it does not change;
 * 4. reads the copy back: the tags and pictures must be the ones written, the audio data (hashed independently
 *    of TagLib, [AudioFingerprint]), duration and sample rate must be the original ones;
 * 5. backs up the original and records the replacement in the [WriteJournal];
 * 6. overwrites the original with the checked copy and compares the result with the copy;
 * 7. on a failure in step 6 restores the original from the backup; after a crash [TagFileRecovery] does it.
 *
 * Writes run one at a time.
 *
 * @param usableSpace free bytes in a folder; replaced in tests.
 */
internal class SafeTagWriter(
    private val bridge: TagLibBridge,
    private val guard: NativeCallGuard,
    private val journal: WriteJournal,
    private val files: OriginalFiles,
    private val recovery: TagFileRecovery,
    private val dispatchers: DispatcherProvider,
    private val usableSpace: (File) -> Long = File::getUsableSpace,
) : TagWriter {
    private val mutex = Mutex()

    override suspend fun writeTags(
        target: TagWriteTarget,
        edit: TagEdit,
        id3v2VersionForNewTags: Int,
    ): TagWriteResult {
        require(id3v2VersionForNewTags in ID3V2_VERSIONS) { "ID3v2 version must be 3 or 4: $id3v2VersionForNewTags" }
        return mutex.withLock {
            withContext(dispatchers.io) {
                recovery.ensureRecovered()
                preflight(target) ?: write(target, edit, id3v2VersionForNewTags)
            }
        }
    }

    /** A result that stops the write before anything is copied, or null to go on. */
    private fun preflight(target: TagWriteTarget): TagWriteResult? =
        when {
            guard.isQuarantined(target.key) -> TagWriteResult.Quarantined
            files.find(target) == null -> TagWriteResult.Unreadable
            !files.canWrite(target) -> TagWriteResult.NeedsPermission
            // An earlier write may have left this file half-replaced: never back up a damaged original.
            !recoverEarlierWrites(target) -> TagWriteResult.ReplaceFailed(restored = false)
            else -> null
        }

    private fun recoverEarlierWrites(target: TagWriteTarget): Boolean =
        journal.transactions()
            .filter { it.entry()?.target?.key == target.key }
            .all { journal.recover(it, files) }

    /**
     * Runs the write in its own journal folder. The folder is deleted afterwards unless the original may be damaged:
     * after [TagWriteResult.ReplaceFailed] without a restore, or when the process is dying (an [Error] escapes).
     */
    @Suppress("SwallowedException")
    private fun write(
        target: TagWriteTarget,
        edit: TagEdit,
        id3v2VersionForNewTags: Int,
    ): TagWriteResult {
        val original = files.find(target) ?: return TagWriteResult.Unreadable
        val required = original.size() * 2 + SPACE_MARGIN_BYTES
        journal.directory.mkdirs()
        if (usableSpace(journal.directory) < required) return TagWriteResult.NotEnoughSpace(required)

        val transaction = journal.newTransaction()
        var outcome: TagWriteResult? = null
        try {
            outcome =
                try {
                    writeInTransaction(transaction, original, target, edit, id3v2VersionForNewTags)
                } catch (e: IOException) {
                    TagWriteResult.Unreadable
                } catch (e: SecurityException) {
                    TagWriteResult.NeedsPermission
                }
            return outcome
        } finally {
            val originalIntact = outcome != null && outcome != TagWriteResult.ReplaceFailed(restored = false)
            if (!transaction.hasEntry() || originalIntact) transaction.delete()
        }
    }

    private fun writeInTransaction(
        transaction: WriteJournal.Transaction,
        original: OriginalFile,
        target: TagWriteTarget,
        edit: TagEdit,
        id3v2VersionForNewTags: Int,
    ): TagWriteResult {
        val workingCopy = transaction.workingCopy(target.fileName)
        val oldSha256 = copyWithSha256(original.openInput(), workingCopy)
        val failure = prepareWorkingCopy(target, workingCopy, edit, id3v2VersionForNewTags)
        if (failure != null) return failure

        val backup = transaction.backup(target.fileName)
        if (!copyWithSha256(original.openInput(), backup).contentEquals(oldSha256)) return TagWriteResult.FileChanged
        val entry = WriteJournal.Entry(target, oldSha256, newSha256 = sha256(workingCopy.inputStream()))
        transaction.begin(entry)
        return replace(original, workingCopy, backup, entry)
    }

    /** The working copy as read before the write. */
    private class Snapshot(val tags: NativeTags, val format: AudioFormat, val audioSha256: ByteArray)

    /** Writes the new tags into [workingCopy] and checks them; null when the copy is ready to replace the original. */
    private fun prepareWorkingCopy(
        target: TagWriteTarget,
        workingCopy: File,
        edit: TagEdit,
        id3v2VersionForNewTags: Int,
    ): TagWriteResult? {
        val before = snapshot(target, workingCopy) ?: return TagWriteResult.Unreadable
        val format = before.format
        val id3v2Version = if (format == AudioFormat.MP3) before.tags.id3v2Version ?: id3v2VersionForNewTags else null
        val properties = PropertyEditor.apply(before.tags.properties, edit, format, id3v2Version)
        val saved =
            guard.runGuarded(target.key) {
                bridge.writeTags(workingCopy.path, properties, edit.cover, id3v2Version ?: id3v2VersionForNewTags)
            }
        if (!saved) return TagWriteResult.WriteFailed

        val expected = Expected(id3v2Version, properties, expectedPictureCount(before.tags, edit.cover))
        return verify(target, workingCopy, before, expected, edit.cover)?.let { TagWriteResult.VerificationFailed(it) }
    }

    private fun snapshot(
        target: TagWriteTarget,
        workingCopy: File,
    ): Snapshot? {
        val tags = readGuarded(target, workingCopy)
        val format = tags?.format
        val audio = format?.let { AudioFingerprint.of(workingCopy, it) }
        return if (tags != null && format != null && audio != null) Snapshot(tags, format, audio) else null
    }

    /** What the working copy must contain after the write, besides the audio of the [Snapshot]. */
    private class Expected(
        val id3v2Version: Int?,
        val properties: Map<String, List<String>>,
        val pictureCount: Int,
    )

    /** Null when the working copy has exactly the expected tags and the original audio, else what differs. */
    private fun verify(
        target: TagWriteTarget,
        workingCopy: File,
        before: Snapshot,
        expected: Expected,
        cover: CoverEdit,
    ): String? {
        val after = readGuarded(target, workingCopy) ?: return "the written copy cannot be read"
        val audioAfter = AudioFingerprint.of(workingCopy, before.format)
        return when {
            after.format != before.format -> "format ${after.format} instead of ${before.format}"
            after.id3v2Version != null && after.id3v2Version != expected.id3v2Version ->
                "ID3v2.${after.id3v2Version} instead of ID3v2.${expected.id3v2Version}"
            after.properties != expected.properties -> "tags ${after.properties} instead of ${expected.properties}"
            after.pictureCount != expected.pictureCount ->
                "${after.pictureCount} pictures instead of ${expected.pictureCount}"
            cover is CoverEdit.Replace && !coverMatches(target, workingCopy, cover) -> "the new cover was not stored"
            audioAfter == null || !audioAfter.contentEquals(before.audioSha256) -> "the audio data changed"
            after.audio?.durationMs != before.tags.audio?.durationMs -> "the duration changed"
            after.audio?.sampleRateHz != before.tags.audio?.sampleRateHz -> "the sample rate changed"
            else -> null
        }
    }

    private fun coverMatches(
        target: TagWriteTarget,
        workingCopy: File,
        cover: CoverEdit.Replace,
    ): Boolean {
        val stored = guard.runGuarded(target.key) { bridge.readCover(TagSource.FilePath(workingCopy.path)) }
        return stored != null && stored.data.contentEquals(cover.picture.data) &&
            stored.mimeType == cover.picture.mimeType
    }

    private fun expectedPictureCount(
        before: NativeTags,
        cover: CoverEdit,
    ): Int =
        when (cover) {
            CoverEdit.Keep -> before.pictureCount
            CoverEdit.Remove -> 0
            is CoverEdit.Replace -> maxOf(before.pictureCount, 1)
        }

    /** Overwrites the original with the checked copy; restores it from [backup] when that fails. */
    private fun replace(
        original: OriginalFile,
        workingCopy: File,
        backup: File,
        entry: WriteJournal.Entry,
    ): TagWriteResult {
        val replaced =
            failSafe {
                original.overwriteWith(workingCopy)
                sha256(original.openInput()).contentEquals(entry.newSha256)
            }
        if (replaced) return TagWriteResult.Success
        val restored = failSafe { WriteJournal.restoreIfNeeded(original, backup, entry, acceptNew = false) }
        return TagWriteResult.ReplaceFailed(restored)
    }

    /** [block], or false when it fails with an I/O or permission error. */
    @Suppress("SwallowedException")
    private inline fun failSafe(block: () -> Boolean): Boolean =
        try {
            block()
        } catch (e: IOException) {
            false
        } catch (e: SecurityException) {
            false
        }

    private fun readGuarded(
        target: TagWriteTarget,
        workingCopy: File,
    ): NativeTags? =
        guard.runGuarded(target.key) { bridge.readTags(TagSource.FilePath(workingCopy.path), withAudioInfo = true) }

    private companion object {
        /** Room left for the journal and the rest of the app after both copies. */
        const val SPACE_MARGIN_BYTES = 1024L * 1024L

        val ID3V2_VERSIONS = setOf(3, 4)
    }
}
