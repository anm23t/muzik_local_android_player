package org.muzzzlo.player.core.tags

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.muzzzlo.player.core.model.AudioFormat
import org.muzzzlo.player.core.model.EmbeddedPicture
import org.muzzzlo.player.core.model.FileTags
import org.muzzzlo.player.core.model.ReplayGainInfo
import org.muzzzlo.player.core.model.TagReadResult
import org.muzzzlo.player.core.model.TagSource
import org.muzzzlo.player.core.model.TrackTags
import java.io.File
import java.io.FileInputStream
import kotlin.math.abs

/**
 * Reads the reference fixtures (`src/test/resources/tags`, made by `src/test/fixtures/make_fixtures.sh`)
 * through the host build of the JNI bridge. Expected values mirror `tag_fixtures.py`.
 */
class TagLibTagReaderTest {
    @TempDir
    lateinit var tempDir: File

    private val reader by lazy {
        testTagReader(NativeCallGuard(File(tempDir, "guard")))
    }

    /** One tagged fixture per way the formats store the same logical tags. */
    enum class Reference(
        val fileName: String,
        val format: AudioFormat,
        val id3v2Version: Int?,
        val artists: List<String>,
        val sampleRateHz: Int,
        val channels: Int,
        val coverType: Int,
    ) {
        MP3_ID3V23("mp3_v23.mp3", AudioFormat.MP3, 3, listOf("Alpha; Beta"), 22050, 1, FRONT),
        MP3_ID3V24("mp3_v24.mp3", AudioFormat.MP3, 4, listOf("Alpha", "Beta"), 22050, 1, FRONT),
        FLAC("flac.flac", AudioFormat.FLAC, null, listOf("Alpha", "Beta"), 8000, 1, FRONT),
        M4A("m4a.m4a", AudioFormat.M4A, null, listOf("Alpha", "Beta"), 22050, 1, FRONT),
        OGG_VORBIS("ogg.ogg", AudioFormat.OGG_VORBIS, null, listOf("Alpha", "Beta"), 22050, 2, FRONT),
        OPUS("opus.opus", AudioFormat.OPUS, null, listOf("Alpha", "Beta"), 48000, 1, FRONT),
    }

    @ParameterizedTest
    @EnumSource(Reference::class)
    fun `reads all tags`(reference: Reference) =
        runTest {
            val fileTags = readSuccess(TagSource.FilePath(fixture(reference.fileName).path))

            assertEquals(reference.format, fileTags.format)
            assertEquals(reference.id3v2Version, fileTags.id3v2Version)
            assertEquals(expectedTags(reference), fileTags.tags)
        }

    @ParameterizedTest
    @EnumSource(Reference::class)
    fun `reads audio properties`(reference: Reference) =
        runTest {
            val audio = requireNotNull(readSuccess(TagSource.FilePath(fixture(reference.fileName).path)).audio)

            assertTrue(abs(audio.durationMs - TONE_MS) <= DURATION_TOLERANCE_MS) { "duration ${audio.durationMs}" }
            assertEquals(reference.sampleRateHz, audio.sampleRateHz)
            assertEquals(reference.channels, audio.channels)
            assertTrue(audio.bitrateKbps > 0)
        }

    @ParameterizedTest
    @EnumSource(Reference::class)
    fun `picks the front cover over other pictures`(reference: Reference) =
        runTest {
            val cover = reader.readCover(TagSource.FilePath(fixture(reference.fileName).path))

            assertEquals(EmbeddedPicture(frontCoverBytes(), "image/png", reference.coverType), cover)
        }

    @ParameterizedTest
    @ValueSource(strings = ["replaygain_v24.mp3", "replaygain.flac", "replaygain.m4a", "replaygain.opus"])
    fun `reads ReplayGain values in every format`(name: String) =
        runTest {
            val fileTags = readSuccess(TagSource.FilePath(fixture(name).path))

            assertEquals(ReplayGainInfo(-6.5f, 0.912345f, -7.25f, 0.987654f), fileTags.replayGain, name)
            assertEquals(TrackTags(), fileTags.tags, name)
        }

    @Test
    fun `reads the track gain of files with other tags and none from untagged files`() =
        runTest {
            for (name in listOf("extra_v23.mp3", "extra_v24.mp3", "extra.flac", "extra.ogg")) {
                val fileTags = readSuccess(TagSource.FilePath(fixture(name).path))
                assertEquals(ReplayGainInfo(trackGainDb = -6.5f), fileTags.replayGain, name)
            }
            for (name in listOf("untagged.mp3", "untagged.flac", "flac.flac")) {
                assertEquals(ReplayGainInfo.NONE, readSuccess(TagSource.FilePath(fixture(name).path)).replayGain, name)
            }
        }

    @Test
    fun `skips audio properties when not asked`() =
        runTest {
            val fileTags = readSuccess(TagSource.FilePath(fixture("flac.flac").path), withAudioInfo = false)

            assertNull(fileTags.audio)
            assertEquals(TITLE, fileTags.tags.title)
        }

    @Test
    fun `an untagged file gives empty tags and no cover`() =
        runTest {
            for (name in listOf("untagged.mp3", "untagged.flac")) {
                val source = TagSource.FilePath(fixture(name).path)
                val fileTags = readSuccess(source)

                assertEquals(TrackTags(), fileTags.tags, name)
                assertNull(fileTags.id3v2Version, name)
                assertNotNull(fileTags.audio, name)
                assertNull(reader.readCover(source), name)
            }
        }

    @Test
    fun `reads a file whose path has emoji and Cyrillic`() =
        runTest {
            val file = fixture("opus.opus").copyTo(File(tempDir, "трек 🎧.opus"))

            assertEquals(TITLE, readSuccess(TagSource.FilePath(file.path)).tags.title)
        }

    @Test
    fun `reads by file descriptor and leaves it open for the caller`() =
        runTest {
            FileInputStream(fixture("mp3_v24.mp3")).use { stream ->
                val source = TagSource.Descriptor(stream.fd.toIntFd(), key = "content://media/1")

                assertEquals(listOf("Alpha", "Beta"), readSuccess(source).tags.artists)
                assertEquals(frontCoverBytes().toList(), reader.readCover(source)?.data?.toList())
                // The native side read from a duplicate, so the descriptor still works after a rewind.
                stream.channel.position(0)
                assertEquals("ID3", String(stream.readNBytes(3)))
            }
        }

    @Test
    fun `reads by descriptor an MP3 with a second ID3v2 tag after its first frame`() =
        runTest {
            // Found on a real phone: such files open by path (format by extension) but TagLib's content sniffing,
            // used for a descriptor without a name, rejects them because no second frame follows the first.
            val file = File(tempDir, "second-tag.mp3").apply { writeBytes(mp3WithSecondTagAfterFirstFrame()) }

            FileInputStream(file).use { stream ->
                val fd = stream.fd.toIntFd()
                val named = reader.readTags(TagSource.Descriptor(fd, key = "content://media/2", fileName = file.name))
                val unnamed = reader.readTags(TagSource.Descriptor(fd, key = "content://media/2"))

                assertTrue(named is TagReadResult.Success) { "with a file name: $named" }
                assertEquals(TagReadResult.Unreadable, unnamed, "TagLib sniffing changed; the name may be optional now")
            }
        }

    @Test
    fun `a file that is not audio is unreadable`() =
        runTest {
            val file = File(tempDir, "not-audio.mp3").apply { writeText("plain text, not MP3") }

            assertEquals(TagReadResult.Unreadable, reader.readTags(TagSource.FilePath(file.path)))
            assertNull(reader.readCover(TagSource.FilePath(file.path)))
        }

    @Test
    fun `a missing file is unreadable`() =
        runTest {
            val missing = TagSource.FilePath(File(tempDir, "missing.flac").path)

            assertEquals(TagReadResult.Unreadable, reader.readTags(missing))
        }

    @Test
    fun `a file that crashed while read alone is quarantined and not read`() =
        runTest {
            val guardDir = File(tempDir, "crashed")
            val key = fixture("flac.flac").path
            leaveMarker(guardDir, "in-progress-solo", key)
            val reader = testTagReader(NativeCallGuard(guardDir))

            assertEquals(TagReadResult.Quarantined, reader.readTags(TagSource.FilePath(key)))
            assertNull(reader.readCover(TagSource.FilePath(key)))
            assertTrue(reader.readTags(TagSource.FilePath(fixture("ogg.ogg").path)) is TagReadResult.Success)
        }

    @Test
    fun `a file left in progress by an ordinary read is read again alone and cleared`() =
        runTest {
            val guardDir = File(tempDir, "crashed")
            val key = fixture("flac.flac").path
            leaveMarker(guardDir, "in-progress", key)
            val reader = testTagReader(NativeCallGuard(guardDir))

            assertTrue(reader.readTags(TagSource.FilePath(key)) is TagReadResult.Success)
            val guard = NativeCallGuard(guardDir)
            assertTrue(guard.suspects().isEmpty())
            assertTrue(guard.quarantined().isEmpty())
        }

    @Test
    fun `reads Ogg Vorbis whose big cover spans many comment pages, by path`() =
        runTest {
            val guardDir = File(tempDir, "big")
            val reader = testTagReader(NativeCallGuard(guardDir))
            val source = TagSource.FilePath(fixture(BIG_COVER).path)

            assertBigCoverFile(reader.readTags(source), reader.readCover(source))
            assertNothingLeftBehind(guardDir)
        }

    @Test
    fun `reads Ogg Vorbis whose big cover spans many comment pages, by descriptor`() =
        runTest {
            val guardDir = File(tempDir, "big")
            val reader = testTagReader(NativeCallGuard(guardDir))

            FileInputStream(fixture(BIG_COVER)).use { stream ->
                val source = TagSource.Descriptor(stream.fd.toIntFd(), key = "content://media/3", fileName = BIG_COVER)

                assertBigCoverFile(reader.readTags(source), reader.readCover(source))
            }
            assertNothingLeftBehind(guardDir)
        }

    @Test
    fun `big cover file left in progress by a crash elsewhere is read alone and not quarantined`() =
        runTest {
            // The case from task 1.1.2: the process died while this file and others were being read.
            val guardDir = File(tempDir, "big")
            val key = fixture(BIG_COVER).path
            leaveMarker(guardDir, "in-progress", key)
            leaveMarker(guardDir, "in-progress", fixture("ogg.ogg").path)
            val reader = testTagReader(NativeCallGuard(guardDir))
            val source = TagSource.FilePath(key)

            assertBigCoverFile(reader.readTags(source), reader.readCover(source))
            val guard = NativeCallGuard(guardDir)
            assertTrue(guard.quarantined().isEmpty())
            assertEquals(setOf(fixture("ogg.ogg").path), guard.suspects())
        }

    @Test
    fun `a normal read leaves no marker behind`() =
        runTest {
            val guardDir = File(tempDir, "clean")
            val reader = testTagReader(NativeCallGuard(guardDir))
            reader.readTags(TagSource.FilePath(fixture("m4a.m4a").path))

            assertTrue(NativeCallGuard(guardDir).recoverAfterCrash().isEmpty())
        }

    @Test
    fun `rejects a negative file descriptor`() =
        runTest {
            val result = runCatching { reader.readTags(TagSource.Descriptor(-1, "bad")) }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        }

    /** Leaves a marker for [key] in [folder] of [guardDir], as a process killed inside native code does. */
    private fun leaveMarker(
        guardDir: File,
        folder: String,
        key: String,
    ) {
        val markers = File(guardDir, folder).apply { mkdirs() }
        File(markers, "${markers.list().orEmpty().size}-crashed").writeText(key)
    }

    private fun assertBigCoverFile(
        tags: TagReadResult,
        cover: EmbeddedPicture?,
    ) {
        assertTrue(tags is TagReadResult.Success) { "Expected tags of $BIG_COVER, got $tags" }
        val fileTags = (tags as TagReadResult.Success).fileTags
        assertEquals(AudioFormat.OGG_VORBIS, fileTags.format)
        assertEquals("Big cover", fileTags.tags.title)
        assertTrue(fileTags.tags.hasCover)
        assertEquals(BIG_SAMPLE_RATE_HZ, fileTags.audio?.sampleRateHz)
        assertNotNull(cover)
        assertEquals("image/jpeg", cover!!.mimeType)
        assertEquals(FRONT, cover.pictureType)
        assertTrue(cover.data.size > BIG_COVER_MIN_BYTES) { "cover of ${cover.data.size} bytes" }
        // A JPEG starts with SOI and ends with EOI: the whole picture came through all the pages.
        assertEquals(listOf(0xFF, 0xD8), cover.data.take(2).map { it.toInt() and 0xFF })
        assertEquals(listOf(0xFF, 0xD9), cover.data.takeLast(2).map { it.toInt() and 0xFF })
    }

    private fun assertNothingLeftBehind(guardDir: File) {
        val guard = NativeCallGuard(guardDir)
        assertTrue(guard.recoverAfterCrash().isEmpty())
        assertTrue(guard.suspects().isEmpty())
        assertTrue(guard.quarantined().isEmpty())
    }

    /** `untagged.mp3` with a padded ID3v2.4 tag inserted between its first and second MPEG frames. */
    private fun mp3WithSecondTagAfterFirstFrame(): ByteArray {
        val audio = fixture("untagged.mp3").readBytes()
        // CBR frames share the header apart from the padding bit, so the next match is the second frame.
        val second =
            (4 until audio.size - 2).first { i ->
                audio[i] == audio[0] && audio[i + 1] == audio[1] &&
                    (audio[i + 2].toInt() and 0xFD) == (audio[2].toInt() and 0xFD)
            }
        val tagBody = ByteArray(SECOND_TAG_BODY)
        val synchsafeSize = (0..3).map { ((SECOND_TAG_BODY shr (7 * (3 - it))) and 0x7F).toByte() }
        val tagHeader = byteArrayOf(0x49, 0x44, 0x33, 4, 0, 0) + synchsafeSize
        return audio.copyOfRange(0, second) + tagHeader + tagBody + audio.copyOfRange(second, audio.size)
    }

    private suspend fun readSuccess(
        source: TagSource,
        withAudioInfo: Boolean = true,
    ): FileTags {
        val result = reader.readTags(source, withAudioInfo)
        assertTrue(result is TagReadResult.Success) { "Expected tags of ${source.key}, got $result" }
        return (result as TagReadResult.Success).fileTags
    }

    private fun expectedTags(reference: Reference) =
        TrackTags(
            title = TITLE,
            artists = reference.artists,
            album = "Test Album",
            albumArtists = listOf("Alpha"),
            trackNumber = 3,
            trackTotal = 10,
            discNumber = 1,
            discTotal = 2,
            year = 2024,
            genres = listOf("Test"),
            lyrics = LYRICS,
            hasCover = true,
        )

    private fun fixture(name: String) = resourceFile("tags/$name")

    private fun frontCoverBytes() = fixture("cover_front.png").readBytes()

    private companion object {
        const val FRONT = EmbeddedPicture.TYPE_FRONT_COVER
        const val TITLE = "Reference 🎵 тест"
        const val LYRICS = "[00:00.10]Line one\n[00:00.50]Вторая строка 🎶"
        const val TONE_MS = 1000L
        const val SECOND_TAG_BODY = 2048
        const val BIG_COVER = "big_cover.ogg"
        const val BIG_SAMPLE_RATE_HZ = 48000

        /** The fixture's JPEG is about 320 KB; its base64 comment spans several 64 KB Ogg pages. */
        const val BIG_COVER_MIN_BYTES = 300_000

        /** Encoder delay and padding (MP3 frames, Opus pre-skip) make lengths differ slightly from 1 s. */
        const val DURATION_TOLERANCE_MS = 100L
    }
}
