package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WaveformTest {
    private fun peaks(vararg values: Float) = WaveformPeaks(values)

    private fun collect(
        totalFrames: Long,
        count: Int,
        samples: List<Float>,
    ): FloatArray = PeakCollector(totalFrames, count).apply { samples.forEach(::add) }.result().values

    @Test
    fun `collector keeps the loudest frame of each part`() =
        assertArrayEquals(
            floatArrayOf(0.5f, 0.9f),
            collect(4, 2, listOf(0.1f, -0.5f, 0.9f, 0.2f)),
        )

    @Test
    fun `negative samples count by size and over full scale is cut to 1`() =
        assertArrayEquals(floatArrayOf(1f), collect(2, 1, listOf(-0.3f, -1.7f)))

    @Test
    fun `frames past the header length go to the last part`() =
        assertArrayEquals(floatArrayOf(0.1f, 0.8f), collect(2, 2, listOf(0.1f, 0.2f, 0.3f, 0.8f)))

    @Test
    fun `parts never reached stay silent`() =
        assertArrayEquals(floatArrayOf(0.4f, 0f, 0f, 0f), collect(100, 4, listOf(0.4f)))

    @Test
    fun `more parts than frames leaves gaps but keeps the order`() =
        assertArrayEquals(floatArrayOf(0.2f, 0f, 0.6f, 0f), collect(2, 4, listOf(0.2f, 0.6f)))

    @Test
    fun `a track without frames is refused`() {
        assertThrows<IllegalArgumentException> { PeakCollector(0) }
    }

    @Test
    fun `bars are scaled to the loudest`() =
        assertArrayEquals(
            floatArrayOf(Waveform.MIN_BAR + (1 - Waveform.MIN_BAR) * 0.5f, 1f),
            Waveform.bars(peaks(0.1f, 0.25f, 0.5f, 0.3f), 2),
            1e-6f,
        )

    @Test
    fun `a quiet track fills the height like a loud one`() =
        assertArrayEquals(Waveform.bars(peaks(0.5f, 1f), 2), Waveform.bars(peaks(0.05f, 0.1f), 2), 1e-6f)

    @Test
    fun `silence gives the thinnest bars`() {
        assertArrayEquals(FloatArray(3) { Waveform.MIN_BAR }, Waveform.bars(peaks(0f, 0f, 0f, 0f), 3))
        assertArrayEquals(FloatArray(2) { Waveform.MIN_BAR }, Waveform.bars(peaks(), 2))
    }

    @Test
    fun `more bars than peaks repeat the peaks`() =
        assertArrayEquals(
            floatArrayOf(Waveform.MIN_BAR, Waveform.MIN_BAR, 1f, 1f),
            Waveform.bars(peaks(0f, 1f), 4),
        )

    @Test
    fun `no bars for no width`() {
        assertEquals(0, Waveform.bars(peaks(1f), 0).size)
        assertEquals(0, Waveform.bars(peaks(1f), -3).size)
    }

    @Test
    fun `a peak count that does not divide evenly loses no loud peak`() {
        val values = FloatArray(Waveform.PEAK_COUNT) { 0.1f }.also { it[Waveform.PEAK_COUNT - 1] = 0.9f }

        assertEquals(1f, Waveform.bars(WaveformPeaks(values), 333).last())
    }

    @Test
    fun `cache file reads back within a byte step`() {
        val original = peaks(0f, 0.5f, 1f, 0.333f)

        val read = Waveform.decode(Waveform.encode(original))!!

        assertArrayEquals(original.values, read.values, 1f / 255)
        assertEquals(8 + 4, Waveform.encode(original).size)
    }

    @Test
    fun `full size cache file reads back`() {
        val original = WaveformPeaks(FloatArray(Waveform.PEAK_COUNT) { (it % 256) / 255f })

        assertEquals(original, Waveform.decode(Waveform.encode(original)))
    }

    @Test
    fun `damaged cache files are refused`() {
        val good = Waveform.encode(peaks(0.5f, 0.5f))

        assertNull(Waveform.decode(ByteArray(0)))
        assertNull(Waveform.decode(good.copyOf(good.size - 1)))
        assertNull(Waveform.decode(good + 0))
        assertNull(Waveform.decode(good.copyOf().also { it[0] = 'X'.code.toByte() }))
        assertNull(Waveform.decode(good.copyOf().also { it[4] = 9 }))
        assertNull(Waveform.decode(Waveform.encode(peaks())))
    }
}
