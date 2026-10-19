package org.muzzzlo.player.core.tags

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.muzzzlo.player.core.model.ArtistSplitter
import org.muzzzlo.player.core.model.AudioFormat
import org.muzzzlo.player.core.model.CompoundNames
import org.muzzzlo.player.core.model.CoverEdit
import org.muzzzlo.player.core.model.EmbeddedPicture
import org.muzzzlo.player.core.model.TagEdit
import org.muzzzlo.player.core.model.TagReadResult
import org.muzzzlo.player.core.model.TagSource
import org.muzzzlo.player.core.model.TagWriteResult
import org.muzzzlo.player.core.model.TagWriteTarget
import org.muzzzlo.player.core.model.TrackTags
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * The safe write on the host build of TagLib, with the originals as plain files. Fault injection replaces
 * the original's overwrite to simulate failures and a process killed midway.
 */
class SafeTagWriterTest {
    @TempDir
    lateinit var tempDir: File

    private val guardDir by lazy { File(tempDir, "guard") }
    private val journalDir by lazy { File(tempDir, "journal") }

    /** Every fixture the writer must handle, with the ID3v2 version and picture count it has. */
    enum class Fixture(
        val fileName: String,
        val format: AudioFormat,
        val id3v2Version: Int?,
        val pictureCount: Int,
    ) {
        MP3_ID3V23("mp3_v23.mp3", AudioFormat.MP3, 3, 2),
        MP3_ID3V24("mp3_v24.mp3", AudioFormat.MP3, 4, 2),
        MP3_EXTRA_V23("extra_v23.mp3", AudioFormat.MP3, 3, 2),
        MP3_EXTRA_V24("extra_v24.mp3", AudioFormat.MP3, 4, 2),
        MP3_UNTAGGED("untagged.mp3", AudioFormat.MP3, null, 0),
        FLAC("flac.flac", AudioFormat.FLAC, null, 2),
        FLAC_EXTRA("extra.flac", AudioFormat.FLAC, null, 2),
        FLAC_UNTAGGED("untagged.flac", AudioFormat.FLAC, null, 0),
        M4A("m4a.m4a", AudioFormat.M4A, null, 1),
        M4A_EXTRA("extra.m4a", AudioFormat.M4A, null, 1),
        OGG_VORBIS("ogg.ogg", AudioFormat.OGG_VORBIS, null, 2),
        OGG_EXTRA("extra.ogg", AudioFormat.OGG_VORBIS, null, 2),
        OPUS("opus.opus", AudioFormat.OPUS, null, 2),
        OPUS_EXTRA("extra.opus", AudioFormat.OPUS, null, 2),
    }

    @ParameterizedTest
    @EnumSource(Fixture::class)
    fun `writes every field and reads them back`(fixture: Fixture) =
        runTest {
            val file = copyOf(fixture.fileName)

            assertEquals(TagWriteResult.Success, writer().writeTags(target(file), fullEdit))

            val fileTags = readSuccess(file)
            val version = fixture.id3v2Version ?: if (fixture.format == AudioFormat.MP3) 4 else null
            assertEquals(expectedTags(joinArtists = version == 3, hasCover = fixture.pictureCount > 0), fileTags.tags)
            assertEquals(version, fileTags.id3v2Version)
            assertNoTransactionsLeft()
        }

    @ParameterizedTest
    @EnumSource(Fixture::class)
    fun `split artists come back unchanged after a write and a read`(fixture: Fixture) =
        runTest {
            val artists = listOf("Tyler, The Creator", "Kanye West", "Simon & Garfunkel")
            val compounds = CompoundNames { setOf(listOf("tyler", "the creator")) }
            val file = copyOf(fixture.fileName)

            assertEquals(TagWriteResult.Success, writer().writeTags(target(file), TagEdit(artists = artists)))

            assertEquals(artists, ArtistSplitter.split(readSuccess(file).tags.artists, compounds))
        }

    @ParameterizedTest
    @EnumSource(Fixture::class)
    fun `keeps the audio data and every field it does not edit`(fixture: Fixture) =
        runTest {
            val file = copyOf(fixture.fileName)
            val before = rawProperties(file)
            val audioBefore = AudioFingerprint.of(file, fixture.format)

            writer().writeTags(target(file), fullEdit)

            assertEquals(before.withoutEditedKeys(), rawProperties(file).withoutEditedKeys())
            assertArrayEquals(audioBefore, AudioFingerprint.of(file, fixture.format))
            assertEquals(
                fixture.pictureCount,
                TagLibBridge().readTags(TagSource.FilePath(file.path), false)?.pictureCount,
            )
        }

    @Test
    fun `keeps frames TagLib does not map and the trailing APEv2 and ID3v1 tags`() =
        runTest {
            for (name in listOf("extra_v23.mp3", "extra_v24.mp3")) {
                val file = copyOf(name)

                assertEquals(TagWriteResult.Success, writer().writeTags(target(file), fullEdit), name)

                val bytes = file.readBytes()
                assertTrue(bytes.contains("org.muzzzlo.test".toByteArray()), "PRIV frame of $name")
                if (name == "extra_v23.mp3") {
                    assertTrue(bytes.contains("MUZZZLO_APE".toByteArray()), "APEv2 tag")
                    assertEquals("TAG", String(bytes.copyOfRange(bytes.size - 128, bytes.size - 125)), "ID3v1 tag")
                }
            }
        }

    @Test
    fun `adds no ID3v1 tag to a file without one`() =
        runTest {
            val file = copyOf("untagged.mp3")

            writer().writeTags(target(file), fullEdit)

            val bytes = file.readBytes()
            assertFalse(String(bytes.copyOfRange(bytes.size - 128, bytes.size - 125)) == "TAG")
        }

    @Test
    fun `a new ID3v2 tag gets the requested version`() =
        runTest {
            val file = copyOf("untagged.mp3")

            writer().writeTags(target(file), fullEdit, id3v2VersionForNewTags = 3)

            val fileTags = readSuccess(file)
            assertEquals(3, fileTags.id3v2Version)
            assertEquals(listOf("Tyler, The Creator; Kanye West"), fileTags.tags.artists)
        }

    @Test
    fun `an edit equal to the file keeps the stored form of the fields`() =
        runTest {
            val file = copyOf("flac.flac")
            val before = rawProperties(file)
            val edit = TagEdit.from(readSuccess(file).tags)

            assertEquals(TagWriteResult.Success, writer().writeTags(target(file), edit))

            assertEquals(before, rawProperties(file))
            assertEquals(listOf("2024-05-01"), rawProperties(file)["DATE"])
        }

    @ParameterizedTest
    @EnumSource(Fixture::class)
    fun `replaces the front cover and keeps the other pictures`(fixture: Fixture) =
        runTest {
            val file = copyOf(fixture.fileName)

            val result = writer().writeTags(target(file), TagEdit.from(readSuccess(file).tags).copy(cover = newCover))

            assertEquals(TagWriteResult.Success, result)
            assertEquals(newCover.picture, reader().readCover(TagSource.FilePath(file.path)))
            val pictures = TagLibBridge().readTags(TagSource.FilePath(file.path), false)?.pictureCount
            assertEquals(maxOf(fixture.pictureCount, 1), pictures)
        }

    @ParameterizedTest
    @EnumSource(Fixture::class)
    fun `removes every picture`(fixture: Fixture) =
        runTest {
            val file = copyOf(fixture.fileName)

            val result =
                writer().writeTags(target(file), TagEdit.from(readSuccess(file).tags).copy(cover = CoverEdit.Remove))

            assertEquals(TagWriteResult.Success, result)
            assertFalse(readSuccess(file).tags.hasCover)
            assertNull(reader().readCover(TagSource.FilePath(file.path)))
        }

    @Test
    fun `clearing every field leaves a file without tags that still plays`() =
        runTest {
            val file = copyOf("mp3_v24.mp3")
            val audioBefore = AudioFingerprint.of(file, AudioFormat.MP3)

            assertEquals(TagWriteResult.Success, writer().writeTags(target(file), TagEdit(cover = CoverEdit.Remove)))

            assertEquals(TrackTags(), readSuccess(file).tags)
            assertArrayEquals(audioBefore, AudioFingerprint.of(file, AudioFormat.MP3))
        }

    @Test
    fun `a read-only file needs a permission and is not touched`() =
        runTest {
            val file = copyOf("flac.flac").apply { setWritable(false) }
            val bytes = file.readBytes()

            assertEquals(TagWriteResult.NeedsPermission, writer().writeTags(target(file), fullEdit))
            assertArrayEquals(bytes, file.readBytes())
        }

    @Test
    fun `stops when there is no room for two copies`() =
        runTest {
            val file = copyOf("flac.flac")
            val bytes = file.readBytes()

            val result = writer(usableSpace = { 0L }).writeTags(target(file), fullEdit)

            assertEquals(TagWriteResult.NotEnoughSpace(bytes.size * 2L + 1024 * 1024), result)
            assertArrayEquals(bytes, file.readBytes())
            assertNoTransactionsLeft()
        }

    @Test
    fun `a file that is not audio or is missing is unreadable`() =
        runTest {
            val text = File(tempDir, "text.mp3").apply { writeText("plain text, not MP3") }

            assertEquals(TagWriteResult.Unreadable, writer().writeTags(target(text), fullEdit))
            assertEquals(TagWriteResult.Unreadable, writer().writeTags(target(File(tempDir, "no.mp3")), fullEdit))
            assertEquals("plain text, not MP3", text.readText())
            assertNoTransactionsLeft()
        }

    @Test
    fun `a quarantined file is not touched`() =
        runTest {
            val file = copyOf("flac.flac")
            val bytes = file.readBytes()
            simulateNativeCrash(file.path)

            assertEquals(TagWriteResult.Quarantined, writer().writeTags(target(file), fullEdit))
            assertArrayEquals(bytes, file.readBytes())
        }

    @Test
    fun `a failed save leaves the original as it was`() =
        runTest {
            val file = copyOf("ogg.ogg")
            val bytes = file.readBytes()
            val failing =
                object : TagLibBridge() {
                    override fun writeTags(
                        path: String,
                        properties: Map<String, List<String>>,
                        cover: CoverEdit,
                        id3v2Version: Int,
                    ) = false
                }

            assertEquals(TagWriteResult.WriteFailed, writer(bridge = failing).writeTags(target(file), fullEdit))
            assertArrayEquals(bytes, file.readBytes())
            assertNoTransactionsLeft()
        }

    @Test
    fun `damaged audio in the working copy never reaches the original`() =
        runTest {
            val file = copyOf("flac.flac")
            val bytes = file.readBytes()
            val damaging =
                object : TagLibBridge() {
                    override fun writeTags(
                        path: String,
                        properties: Map<String, List<String>>,
                        cover: CoverEdit,
                        id3v2Version: Int,
                    ): Boolean {
                        val saved = super.writeTags(path, properties, cover, id3v2Version)
                        val copy = File(path).readBytes()
                        copy[copy.size - 1] = (copy[copy.size - 1].toInt() xor 0x01).toByte()
                        File(path).writeBytes(copy)
                        return saved
                    }
                }

            val result = writer(bridge = damaging).writeTags(target(file), fullEdit)

            assertEquals(TagWriteResult.VerificationFailed("the audio data changed"), result)
            assertArrayEquals(bytes, file.readBytes())
            assertNoTransactionsLeft()
        }

    @Test
    fun `a file changed by someone else during the write is not overwritten`() =
        runTest {
            val file = copyOf("flac.flac")
            val files =
                FaultyFiles(
                    onOpenInput = { attempt -> if (attempt == 2) file.appendBytes(byteArrayOf(1)) },
                )

            assertEquals(TagWriteResult.FileChanged, writer(files = files).writeTags(target(file), fullEdit))
            assertEquals(fixture("flac.flac").length() + 1, file.length())
            assertNoTransactionsLeft()
        }

    @Test
    fun `a failure while replacing restores the original from the backup`() =
        runTest {
            val file = copyOf("mp3_v24.mp3")
            val bytes = file.readBytes()
            val files =
                FaultyFiles(onOverwrite = {
                        attempt,
                        original,
                        source,
                    ->
                    halfWrite(attempt == 1, original, source)
                })

            val result = writer(files = files).writeTags(target(file), fullEdit)

            assertEquals(TagWriteResult.ReplaceFailed(restored = true), result)
            assertArrayEquals(bytes, file.readBytes())
            assertNoTransactionsLeft()
        }

    @Test
    fun `an original that cannot be restored yet is restored on the next start`() =
        runTest {
            val file = copyOf("opus.opus")
            val bytes = file.readBytes()
            val broken = FaultyFiles(onOverwrite = { _, original, source -> halfWrite(true, original, source) })

            assertEquals(
                TagWriteResult.ReplaceFailed(restored = false),
                writer(files = broken).writeTags(target(file), fullEdit),
            )
            assertFalse(bytes.contentEquals(file.readBytes()))
            // The next write to the same file retries the restore first and refuses to go on while it fails.
            assertEquals(
                TagWriteResult.ReplaceFailed(restored = false),
                writer(files = broken).writeTags(target(file), fullEdit),
            )

            // A new process: the first read restores the original.
            reader().readTags(TagSource.FilePath(file.path))
            assertArrayEquals(bytes, file.readBytes())
            assertNoTransactionsLeft()
        }

    @Test
    fun `a process killed while replacing gets the original back on the next start`() =
        runTest {
            val file = copyOf("m4a.m4a")
            val bytes = file.readBytes()
            val dying = FaultyFiles(onOverwrite = { _, original, source -> dieMidway(original, source) })

            assertFailsWith<ProcessKilled> { writer(files = dying).writeTags(target(file), fullEdit) }
            assertFalse(bytes.contentEquals(file.readBytes()))

            assertEquals(TagWriteResult.Success, writer().writeTags(target(file), fullEdit.copy(title = "Again")))
            assertEquals("Again", readSuccess(file).tags.title)
            assertNoTransactionsLeft()
        }

    @Test
    fun `a process killed right after the replacement keeps the new tags`() =
        runTest {
            val file = copyOf("flac.flac")
            val dying =
                FaultyFiles(onOverwrite = { _, original, source ->
                    source.copyTo(original, overwrite = true)
                    throw ProcessKilled()
                })

            assertFailsWith<ProcessKilled> { writer(files = dying).writeTags(target(file), fullEdit) }

            reader().readTags(TagSource.FilePath(file.path))
            assertEquals(expectedTags(joinArtists = false, hasCover = true), readSuccess(file).tags)
            assertNoTransactionsLeft()
        }

    @Test
    fun `leftovers of a write that never reached the original are deleted on start`() =
        runTest {
            val leftover = WriteJournal(journalDir).newTransaction()
            leftover.workingCopy("song.flac").writeText("half-written copy")

            reader().readTags(TagSource.FilePath(fixture("flac.flac").path))

            assertNoTransactionsLeft()
        }

    @Test
    fun `rejects an ID3v2 version other than 3 or 4`() =
        runTest {
            val file = copyOf("untagged.mp3")

            assertFailsWith<IllegalArgumentException> { writer().writeTags(target(file), fullEdit, 2) }
        }

    // --- helpers ---

    private suspend inline fun <reified T : Throwable> assertFailsWith(block: suspend () -> Unit) {
        val error = runCatching { block() }.exceptionOrNull()
        assertTrue(error is T) { "Expected ${T::class.simpleName}, got $error" }
    }

    /** Thrown by a fault to stand for the process being killed: the writer does not catch errors. */
    private class ProcessKilled : Error()

    /**
     * [PathOriginalFiles] with hooks: [onOpenInput] runs before each read of the original (1-based attempt),
     * [onOverwrite] replaces the overwrite.
     */
    private class FaultyFiles(
        private val onOpenInput: (attempt: Int) -> Unit = {},
        private val onOverwrite: ((attempt: Int, original: File, source: File) -> Unit)? = null,
    ) : OriginalFiles {
        private val paths = PathOriginalFiles()
        private var inputs = 0
        private var overwrites = 0

        override fun canWrite(target: TagWriteTarget): Boolean = paths.canWrite(target)

        override fun find(target: TagWriteTarget): OriginalFile? {
            val delegate = paths.find(target) ?: return null
            val file = File((target as TagWriteTarget.FilePath).path)
            return object : OriginalFile {
                override fun size(): Long = delegate.size()

                override fun openInput(): InputStream {
                    onOpenInput(++inputs)
                    return delegate.openInput()
                }

                override fun overwriteWith(source: File) {
                    val hook = onOverwrite
                    if (hook == null) delegate.overwriteWith(source) else hook(++overwrites, file, source)
                }
            }
        }
    }

    /** Writes the first half of [source] over [original] and fails when [fail] is true; writes it all otherwise. */
    private fun halfWrite(
        fail: Boolean,
        original: File,
        source: File,
    ) {
        if (!fail) {
            source.copyTo(original, overwrite = true)
            return
        }
        val bytes = source.readBytes()
        original.writeBytes(bytes.copyOf(bytes.size / 2))
        throw IOException("Simulated write failure")
    }

    private fun dieMidway(
        original: File,
        source: File,
    ) {
        val bytes = source.readBytes()
        original.writeBytes(bytes.copyOf(bytes.size / 2))
        throw ProcessKilled()
    }

    private fun writer(
        bridge: TagLibBridge = TagLibBridge(),
        files: OriginalFiles = PathOriginalFiles(),
        usableSpace: (File) -> Long = File::getUsableSpace,
    ): SafeTagWriter {
        val guard = NativeCallGuard(guardDir)
        val journal = WriteJournal(journalDir)
        // A new writer stands for a new process: its recovery runs again.
        val recovery = TagFileRecovery(guard, journal, files)
        return SafeTagWriter(bridge, guard, journal, files, recovery, TestDispatchers, usableSpace)
    }

    private fun reader(): TagLibTagReader {
        val guard = NativeCallGuard(guardDir)
        return TagLibTagReader(
            TagLibBridge(),
            guard,
            TagFileRecovery(guard, WriteJournal(journalDir), PathOriginalFiles()),
            TestDispatchers,
        )
    }

    private suspend fun readSuccess(file: File) =
        (reader().readTags(TagSource.FilePath(file.path)) as TagReadResult.Success).fileTags

    private fun rawProperties(file: File) =
        requireNotNull(TagLibBridge().readTags(TagSource.FilePath(file.path), false)).properties

    private fun Map<String, List<String>>.withoutEditedKeys() =
        filterKeys { key -> key !in EDITED_KEYS && !key.startsWith("LYRICS:") }

    private fun assertNoTransactionsLeft() {
        assertTrue(journalDir.listFiles().orEmpty().isEmpty()) { "left in the journal: ${journalDir.list()?.toList()}" }
    }

    /** Puts [key] into quarantine the way a native crash does. */
    private fun simulateNativeCrash(key: String) {
        val markers = File(guardDir, "in-progress").apply { mkdirs() }
        File(markers, "1-crashed").writeText(key)
    }

    private fun target(file: File) = TagWriteTarget.FilePath(file.path)

    private fun copyOf(name: String): File = fixture(name).copyTo(File(tempDir, "music/$name"))

    private fun fixture(name: String) = resourceFile("tags/$name")

    private fun ByteArray.contains(part: ByteArray): Boolean =
        (0..size - part.size).any { start -> part.indices.all { this[start + it] == part[it] } }

    private val fullEdit =
        TagEdit(
            title = "Новое 🎧 name",
            artists = listOf("Tyler, The Creator", "Kanye West"),
            album = "New Album",
            albumArtists = listOf("Tyler, The Creator"),
            trackNumber = 5,
            trackTotal = 12,
            discNumber = 2,
            discTotal = 3,
            year = 2021,
            genres = listOf("Hip-Hop"),
            lyrics = "[00:01.00]Строка\n[00:02.00]Line 🎶",
        )

    private fun expectedTags(
        joinArtists: Boolean,
        hasCover: Boolean,
    ) = TrackTags(
        title = "Новое 🎧 name",
        artists =
            if (joinArtists) {
                listOf(
                    "Tyler, The Creator; Kanye West",
                )
            } else {
                listOf("Tyler, The Creator", "Kanye West")
            },
        album = "New Album",
        albumArtists = listOf("Tyler, The Creator"),
        trackNumber = 5,
        trackTotal = 12,
        discNumber = 2,
        discTotal = 3,
        year = 2021,
        genres = listOf("Hip-Hop"),
        lyrics = "[00:01.00]Строка\n[00:02.00]Line 🎶",
        hasCover = hasCover,
    )

    private val newCover by lazy {
        CoverEdit.Replace(
            EmbeddedPicture(fixture("cover_other.png").readBytes(), "image/png", EmbeddedPicture.TYPE_FRONT_COVER),
        )
    }

    private companion object {
        val EDITED_KEYS =
            setOf(
                "TITLE", "ARTIST", "ALBUM", "ALBUMARTIST", "TRACKNUMBER", "TRACKTOTAL", "TOTALTRACKS",
                "DISCNUMBER", "DISCTOTAL", "TOTALDISCS", "DATE", "YEAR", "GENRE", "LYRICS", "UNSYNCEDLYRICS",
            )
    }
}
