package org.muzzzlo.player.core.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.muzzzlo.player.core.model.EqualizerPreset
import org.muzzzlo.player.core.model.EqualizerSettings
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin

class EqualizerAudioProcessorTest {
    private fun band(
        index: Int,
        db: Float,
        preamp: Float = 0f,
    ) = EqualizerSettings(
        enabled = true,
        preset = EqualizerPreset.CUSTOM,
        customDb = List(10) { if (it == index) db else 0f },
        preampDb = preamp,
    )

    private fun processor(
        settings: EqualizerSettings,
        clipProtection: Boolean = false,
        channels: Int = 1,
        encoding: Int = C.ENCODING_PCM_FLOAT,
    ) = EqualizerAudioProcessor(settings, clipProtection).apply {
        configure(AudioFormat(SAMPLE_RATE, channels, encoding))
        flush()
    }

    private fun sine(
        frequency: Double,
        amplitude: Double,
        frames: Int = SAMPLE_RATE / 2,
    ) = FloatArray(frames) { (amplitude * sin(2 * PI * frequency * it / SAMPLE_RATE)).toFloat() }

    /** Peak of the second half, after the filters settled. */
    private fun FloatArray.settledPeak(): Double = drop(size / 2).maxOf { abs(it.toDouble()) }

    private fun gainDb(
        processor: EqualizerAudioProcessor,
        frequency: Double,
    ): Double {
        val out = processor.processFloat(*sine(frequency, 0.1))
        return 20 * log10(out.settledPeak() / 0.1)
    }

    @ParameterizedTest(name = "band {0} at {1} Hz, {2} dB")
    @CsvSource(
        "5, 1000, 6",
        "5, 1000, -9",
        "2, 125, 12",
        "8, 8000, 4.5",
    )
    fun `a sine at a band centre gets the band gain`(
        index: Int,
        frequency: Double,
        db: Float,
    ) = assertEquals(db.toDouble(), gainDb(processor(band(index, db)), frequency), 0.1)

    @Test
    fun `a sine far from the band is left as it is`() = assertEquals(0.0, gainDb(processor(band(5, 12f)), 60.0), 0.1)

    @Test
    fun `a neutral equalizer copies 16 bit samples exactly`() {
        val samples = shortArrayOf(0, 1, -1, 32767, -32768, 1234)

        assertArrayEquals(samples, processor(EqualizerSettings(), encoding = C.ENCODING_PCM_16BIT).process16(*samples))
        assertArrayEquals(
            samples,
            processor(EqualizerSettings(enabled = true), encoding = C.ENCODING_PCM_16BIT).process16(*samples),
        )
    }

    @Test
    fun `preamp scales every frequency`() =
        assertEquals(-6.0, gainDb(processor(band(5, 0f, preamp = -6f)), 300.0), 0.05)

    @Test
    fun `clip protection keeps a boosted full-scale sine at full scale`() {
        val boosted = processor(band(5, 12f), clipProtection = true)

        val out = boosted.processFloat(*sine(1000.0, 1.0))

        assertTrue(out.settledPeak() <= 1.0 + 1e-3, "${out.settledPeak()}")
        assertTrue(out.settledPeak() > 0.95, "${out.settledPeak()}")
    }

    @Test
    fun `without clip protection the boost is clamped at full scale`() {
        val out = processor(band(5, 12f)).processFloat(*sine(1000.0, 1.0))

        assertEquals(1.0, out.settledPeak(), 1e-6)
    }

    @Test
    fun `16 bit boost clamps instead of wrapping`() {
        val samples = sine(1000.0, 0.9).map { (it * 32767).toInt().toShort() }.toShortArray()

        val out = processor(band(5, 12f), encoding = C.ENCODING_PCM_16BIT).process16(*samples)

        assertEquals(32767, out.max().toInt())
        assertEquals(-32768, out.min().toInt())
    }

    @Test
    fun `channels are filtered apart`() {
        val processor = processor(band(5, 6f), channels = 2)
        val left = sine(1000.0, 0.1)
        val interleaved = FloatArray(left.size * 2) { if (it % 2 == 0) left[it / 2] else 0f }

        val out = processor.processFloat(*interleaved)

        assertEquals(0.0, out.filterIndexed { i, _ -> i % 2 == 1 }.maxOf { abs(it.toDouble()) }, 0.0)
        assertEquals(0.2, out.filterIndexed { i, _ -> i % 2 == 0 }.toFloatArray().settledPeak(), 0.005)
    }

    @Test
    fun `new settings apply to the next buffer`() {
        val processor = processor(EqualizerSettings())
        processor.processFloat(*sine(1000.0, 0.1, 100))

        processor.settings = band(5, 6f)

        assertEquals(6.0, gainDb(processor, 1000.0), 0.1)
    }

    @Test
    fun `flush clears the filter state`() {
        val processor = processor(band(5, 12f))
        processor.processFloat(*sine(1000.0, 1.0, 500))

        processor.flush()
        val out = processor.processFloat(0f, 0f, 0f)

        assertArrayEquals(floatArrayOf(0f, 0f, 0f), out)
    }

    private fun EqualizerAudioProcessor.process16(vararg samples: Short): ShortArray {
        val input = buffer(samples.size * Short.SIZE_BYTES)
        samples.forEach(input::putShort)
        input.flip()
        queueInput(input)
        val output = getOutput().order(ByteOrder.nativeOrder())
        return ShortArray(output.remaining() / Short.SIZE_BYTES) { output.getShort() }
    }

    private fun EqualizerAudioProcessor.processFloat(vararg samples: Float): FloatArray {
        val input = buffer(samples.size * Float.SIZE_BYTES)
        samples.forEach(input::putFloat)
        input.flip()
        queueInput(input)
        val output = getOutput().order(ByteOrder.nativeOrder())
        return FloatArray(output.remaining() / Float.SIZE_BYTES) { output.getFloat() }
    }

    private fun buffer(size: Int) = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())

    private companion object {
        const val SAMPLE_RATE = 44_100
    }
}
