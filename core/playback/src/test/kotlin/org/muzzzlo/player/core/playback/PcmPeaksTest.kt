package org.muzzzlo.player.core.playback

import androidx.media3.common.C
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.muzzzlo.player.core.model.PeakCollector
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmPeaksTest {
    private fun buffer(
        size: Int,
        fill: ByteBuffer.() -> Unit,
    ): ByteBuffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply(fill).also { it.flip() }

    private fun peaksOf(
        encoding: Int,
        channels: Int,
        frames: Long,
        data: ByteBuffer,
    ): FloatArray {
        val collector = PeakCollector(frames, frames.toInt())
        PcmPeaks(collector, encoding, channels).add(data)
        return collector.result().values
    }

    @Test
    fun `16 bit stereo takes the louder channel of each frame`() {
        val data =
            buffer(8) {
                putShort(16384)
                putShort(-32768)
                putShort(-8192)
                putShort(0)
            }

        assertArrayEquals(floatArrayOf(1f, 0.25f), peaksOf(C.ENCODING_PCM_16BIT, 2, 2, data), 1e-6f)
    }

    @Test
    fun `24 bit mono with negative samples`() {
        val data =
            buffer(6) {
                // 0x400000 = half scale, 0xC00000 = minus half scale.
                put(0)
                put(0)
                put(0x40)
                put(0)
                put(0)
                put(0xC0.toByte())
            }

        assertArrayEquals(floatArrayOf(0.5f, 0.5f), peaksOf(C.ENCODING_PCM_24BIT, 1, 2, data), 1e-6f)
    }

    @Test
    fun `32 bit and float`() {
        val ints = buffer(4) { putInt(Int.MIN_VALUE) }
        val floats = buffer(4) { putFloat(-0.75f) }

        assertArrayEquals(floatArrayOf(1f), peaksOf(C.ENCODING_PCM_32BIT, 1, 1, ints), 1e-6f)
        assertArrayEquals(floatArrayOf(0.75f), peaksOf(C.ENCODING_PCM_FLOAT, 1, 1, floats), 1e-6f)
    }

    @Test
    fun `partial frame is skipped and the buffer position is left alone`() {
        val data =
            buffer(6) {
                putShort(8192)
                putShort(8192)
                putShort(32767)
            }
        val collector = PeakCollector(4, 4)

        PcmPeaks(collector, C.ENCODING_PCM_16BIT, 2).add(data)

        assertEquals(1L, collector.frames)
        assertEquals(0, data.position())
    }

    @Test
    fun `unknown encodings are refused`() {
        assertThrows<IllegalArgumentException> { PcmPeaks(PeakCollector(1), C.ENCODING_PCM_8BIT, 1) }
    }
}
