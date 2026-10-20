package org.muzzzlo.player.core.playback

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Decodes the reference files in `src/test/resources/alac` (synthetic tones encoded with macOS `afconvert`) with
 * the host build of libalac_jni. ALAC is lossless, so the samples must equal the source WAV byte for byte.
 */
class AlacStreamDecoderTest {
    @ParameterizedTest(name = "{0}")
    @CsvSource(
        "tone-16bit, 16, 44100, 13230",
        "tone-24bit, 24, 48000, 9600",
    )
    fun `decodes a reference file to the source samples`(
        name: String,
        bitDepth: Int,
        sampleRate: Int,
        frames: Int,
    ) {
        val track = Mp4AlacTrack.read(resource("alac/$name.m4a"))
        val decoded = ByteArrayOutputStream()
        var decodedFrames = 0

        AlacStreamDecoder.create(listOf(track.cookie)).use { decoder ->
            assertEquals(AlacConfig(4096, bitDepth, 2, sampleRate), decoder.config)
            val output = ByteBuffer.allocateDirect(decoder.config.maxOutputSize)
            track.packets.forEach { packet ->
                decodedFrames += decoder.decode(direct(packet), output)
                decoded.write(ByteArray(output.remaining()).also { output.get(it) })
            }
        }

        assertEquals(frames, decodedFrames)
        assertArrayEquals(wavSamples(resource("alac/$name.wav")), decoded.toByteArray())
    }

    @Test
    fun `the last packet of a file is a partial frame`() {
        val track = Mp4AlacTrack.read(resource("alac/tone-24bit.m4a"))
        AlacStreamDecoder.create(listOf(track.cookie)).use { decoder ->
            val output = ByteBuffer.allocateDirect(decoder.config.maxOutputSize)
            val frames = track.packets.map { decoder.decode(direct(it), output) }
            assertEquals(listOf(4096, 4096, 1408), frames)
            assertEquals(1408 * decoder.config.bytesPerFrame, output.limit())
        }
    }

    @Test
    fun `a packet declaring more samples than a frame is refused`() {
        // Single channel element, partial frame with 2^20 samples, uncompressed: the patched check in
        // ALACDecoder.cpp stops it before the decoder writes past its buffers.
        val header = "000" + "0000" + "0".repeat(12) + "1" + "00" + "1"
        val packet = bits(header + Integer.toBinaryString(1 shl 20).padStart(32, '0'))
        AlacStreamDecoder.create(listOf(AlacConfigTest.cookie(4096, 16, 2, 44100))).use { decoder ->
            val output = ByteBuffer.allocateDirect(decoder.config.maxOutputSize)
            val error = assertThrows<AlacDecoderException> { decoder.decode(direct(packet), output) }
            assertTrue(error.message!!.contains("Corrupt"))
        }
    }

    @Test
    fun `a truncated packet is refused or decoded without crashing`() {
        val track = Mp4AlacTrack.read(resource("alac/tone-16bit.m4a"))
        AlacStreamDecoder.create(listOf(track.cookie)).use { decoder ->
            val output = ByteBuffer.allocateDirect(decoder.config.maxOutputSize)
            listOf(1, 2, 7, 64, track.packets[0].size / 2).forEach { size ->
                runCatching { decoder.decode(direct(track.packets[0].copyOf(size)), output) }
                    .onFailure { assertTrue(it is AlacDecoderException, "$size bytes: $it") }
            }
        }
    }

    @Test
    fun `an output buffer smaller than a frame is refused`() {
        val track = Mp4AlacTrack.read(resource("alac/tone-16bit.m4a"))
        AlacStreamDecoder.create(listOf(track.cookie)).use { decoder ->
            val output = ByteBuffer.allocateDirect(decoder.config.maxOutputSize - 1)
            assertThrows<AlacDecoderException> { decoder.decode(direct(track.packets[0]), output) }
        }
    }

    @ParameterizedTest(name = "frame {0}, {1} ch")
    @CsvSource("0, 2", "65537, 2", "4096, 0", "4096, 9")
    fun `the native side refuses cookies outside the limits`(
        frameLength: Int,
        channels: Int,
    ) {
        assertTrue(AlacNative.isAvailable)
        assertEquals(0L, AlacNative.nativeCreate(AlacConfigTest.cookie(frameLength, 16, channels, 44100)))
        assertEquals(0L, AlacNative.nativeCreate(ByteArray(AlacConfig.COOKIE_SIZE - 1)))
    }

    @Test
    fun `an unsupported cookie does not create a decoder`() {
        val cookie = AlacConfigTest.cookie(4096, 8, 2, 44100)
        assertThrows<AlacDecoderException> { AlacStreamDecoder.create(listOf(cookie)) }
    }

    @Test
    fun `a closed decoder cannot decode`() {
        val track = Mp4AlacTrack.read(resource("alac/tone-16bit.m4a"))
        val decoder = AlacStreamDecoder.create(listOf(track.cookie))
        decoder.close()
        assertThrows<IllegalStateException> {
            decoder.decode(direct(track.packets[0]), ByteBuffer.allocateDirect(decoder.config.maxOutputSize))
        }
    }

    private fun resource(name: String): ByteArray =
        requireNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "$name is missing" }.use { it.readBytes() }

    private fun direct(bytes: ByteArray): ByteBuffer =
        ByteBuffer.allocateDirect(bytes.size).put(bytes).flip() as ByteBuffer

    /** Bytes of a bit string, padded with zero bits and a few zero bytes. */
    private fun bits(value: String): ByteArray {
        val padded = value.padEnd((value.length + 7) / 8 * 8 + 64, '0')
        return ByteArray(padded.length / 8) { padded.substring(it * 8, it * 8 + 8).toInt(2).toByte() }
    }

    /** The `data` chunk of a PCM WAV file. */
    private fun wavSamples(wav: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(wav).order(java.nio.ByteOrder.LITTLE_ENDIAN).position(12) as ByteBuffer
        while (buffer.remaining() >= 8) {
            val id = String(ByteArray(4).also { buffer.get(it) }, Charsets.US_ASCII)
            val size = buffer.int
            if (id == "data") return ByteArray(size).also { buffer.get(it) }
            buffer.position(buffer.position() + size)
        }
        error("No data chunk")
    }
}
