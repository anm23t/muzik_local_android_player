package org.muzzzlo.player.core.tags

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.muzzzlo.player.core.model.AudioFormat
import java.io.File

class AudioFingerprintTest {
    @TempDir
    lateinit var tempDir: File

    /**
     * Fixtures that share the same audio: the tagged ones were made by tagging copies of one encoded tone
     * (`make_fixtures.sh`), so only their tags differ.
     */
    enum class SameAudio(
        val format: AudioFormat,
        val fileNames: List<String>,
    ) {
        MP3(AudioFormat.MP3, listOf("untagged.mp3", "mp3_v23.mp3", "mp3_v24.mp3", "extra_v23.mp3", "extra_v24.mp3")),
        FLAC(AudioFormat.FLAC, listOf("untagged.flac", "flac.flac", "extra.flac")),
        M4A(AudioFormat.M4A, listOf("m4a.m4a", "extra.m4a")),
        OGG_VORBIS(AudioFormat.OGG_VORBIS, listOf("ogg.ogg", "extra.ogg")),
        OPUS(AudioFormat.OPUS, listOf("opus.opus", "extra.opus")),
    }

    @ParameterizedTest
    @EnumSource(SameAudio::class)
    fun `tags do not change the fingerprint`(group: SameAudio) {
        val fingerprints = group.fileNames.map { requireNotNull(AudioFingerprint.of(fixture(it), group.format)) { it } }

        fingerprints.drop(1).forEachIndexed { index, fingerprint ->
            assertArrayEquals(fingerprints.first(), fingerprint, group.fileNames[index + 1])
        }
    }

    @ParameterizedTest
    @EnumSource(SameAudio::class)
    fun `one changed audio byte changes the fingerprint`(group: SameAudio) {
        val source = fixture(group.fileNames.last())
        val bytes = source.readBytes()
        val offset = audioOffset(bytes, group.format)
        bytes[offset] = (bytes[offset].toInt() xor 0x01).toByte()
        val damaged = File(tempDir, source.name).apply { writeBytes(bytes) }

        val before = AudioFingerprint.of(source, group.format)
        val after = AudioFingerprint.of(damaged, group.format)

        assertNotNull(after)
        assertFalse(before.contentEquals(after))
    }

    @Test
    fun `a file without the container structure has no fingerprint`() {
        val file = File(tempDir, "text.flac").apply { writeText("plain text") }

        // MP3 has no container: any bytes hash as audio, and TagLib rejects a file without MPEG frames earlier.
        for (format in AudioFormat.entries - AudioFormat.MP3) assertNull(AudioFingerprint.of(file, format), format.name)
    }

    @Test
    fun `a truncated Ogg page has no fingerprint`() {
        val bytes = fixture("opus.opus").readBytes()
        val file = File(tempDir, "cut.opus").apply { writeBytes(bytes.copyOf(bytes.size - 10)) }

        assertNull(AudioFingerprint.of(file, AudioFormat.OPUS))
    }

    /** An offset inside the audio data: before the trailing tags of MP3, inside `mdat` of M4A, the end otherwise. */
    private fun audioOffset(
        bytes: ByteArray,
        format: AudioFormat,
    ): Int =
        when (format) {
            AudioFormat.MP3 -> bytes.size - ID3V1_AND_APE - 10
            AudioFormat.M4A -> String(bytes, Charsets.ISO_8859_1).indexOf("mdat") + 20
            else -> bytes.size - 1
        }

    private fun fixture(name: String) = resourceFile("tags/$name")

    private companion object {
        /** `extra_v24.mp3` has neither, but stepping back over their size still lands in the audio. */
        const val ID3V1_AND_APE = 128 + 88
    }
}
