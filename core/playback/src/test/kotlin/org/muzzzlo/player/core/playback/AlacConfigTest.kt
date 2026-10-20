package org.muzzzlo.player.core.playback

import androidx.media3.common.C
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class AlacConfigTest {
    @ParameterizedTest(name = "frame {0}, {1} bit, {2} ch, {3} Hz -> encoding {4}, {5} bytes per frame")
    @CsvSource(
        "4096, 16, 2, 44100, ${C.ENCODING_PCM_16BIT}, 4",
        "4096, 20, 2, 96000, ${C.ENCODING_PCM_24BIT}, 6",
        "4096, 24, 1, 48000, ${C.ENCODING_PCM_24BIT}, 3",
        "4096, 32, 2, 192000, ${C.ENCODING_PCM_32BIT}, 8",
        "1, 16, 8, 8000, ${C.ENCODING_PCM_16BIT}, 16",
        "65536, 16, 1, 44100, ${C.ENCODING_PCM_16BIT}, 2",
    )
    fun `a supported cookie gives the output layout`(
        frameLength: Int,
        bitDepth: Int,
        channels: Int,
        sampleRate: Int,
        encoding: Int,
        bytesPerFrame: Int,
    ) {
        val config = AlacConfig.parse(listOf(cookie(frameLength, bitDepth, channels, sampleRate)))!!

        assertEquals(AlacConfig(frameLength, bitDepth, channels, sampleRate), config)
        assertEquals(encoding, config.pcmEncoding)
        assertEquals(bytesPerFrame, config.bytesPerFrame)
        assertEquals(frameLength * bytesPerFrame, config.maxOutputSize)
    }

    @ParameterizedTest(name = "frame {0}, {1} bit, {2} ch, {3} Hz is refused")
    @CsvSource(
        "0, 16, 2, 44100",
        "65537, 16, 2, 44100",
        "-1, 16, 2, 44100",
        "4096, 8, 2, 44100",
        "4096, 0, 2, 44100",
        "4096, 28, 2, 44100",
        "4096, 16, 0, 44100",
        "4096, 16, 9, 44100",
        "4096, 16, 2, 0",
        "4096, 16, 2, -1",
    )
    fun `an unsupported cookie is refused`(
        frameLength: Int,
        bitDepth: Int,
        channels: Int,
        sampleRate: Int,
    ) {
        assertNull(AlacConfig.parse(listOf(cookie(frameLength, bitDepth, channels, sampleRate))))
    }

    @Test
    fun `missing or extra initialization data is refused`() {
        val good = cookie(4096, 16, 2, 44100)
        assertNull(AlacConfig.parse(emptyList()))
        assertNull(AlacConfig.parse(listOf(good, good)))
        assertNull(AlacConfig.parse(listOf(good.copyOf(23))))
        assertNull(AlacConfig.parse(listOf(good.copyOf(25))))
    }

    @Test
    fun `a cookie with the alac atom header is read without it`() {
        val config = cookie(4096, 24, 2, 48000)
        val withHeader = byteArrayOf(0, 0, 0, 36) + "alac".toByteArray() + byteArrayOf(0, 0, 0, 0) + config

        assertArrayEquals(config, AlacConfig.withoutAtomHeader(withHeader))
        assertEquals(AlacConfig(4096, 24, 2, 48000), AlacConfig.parse(listOf(withHeader)))
    }

    @Test
    fun `a 36-byte cookie of another atom is left as is`() {
        val other = ByteArray(36).also { "frma".toByteArray().copyInto(it, destinationOffset = 4) }
        assertArrayEquals(other, AlacConfig.withoutAtomHeader(other))
        assertNull(AlacConfig.parse(listOf(other)))
    }

    companion object {
        /** An `ALACSpecificConfig` with the usual tuning values (pb 40, mb 10, kb 14, max run 255). */
        fun cookie(
            frameLength: Int,
            bitDepth: Int,
            channels: Int,
            sampleRate: Int,
        ): ByteArray =
            java.nio.ByteBuffer.allocate(AlacConfig.COOKIE_SIZE)
                .putInt(frameLength)
                .put(0)
                .put(bitDepth.toByte())
                .put(40)
                .put(10)
                .put(14)
                .put(channels.toByte())
                .putShort(255)
                .putInt(0)
                .putInt(0)
                .putInt(sampleRate)
                .array()
    }
}
