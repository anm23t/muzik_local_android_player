package org.muzzzlo.player.core.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.muzzzlo.player.core.model.ReplayGain
import org.muzzzlo.player.core.model.ReplayGainInfo
import org.muzzzlo.player.core.model.ReplayGainMode
import org.muzzzlo.player.core.model.ReplayGainSettings
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ReplayGainAudioProcessorTest {
    private val trackOnly = ReplayGainSettings(mode = ReplayGainMode.TRACK, clipProtection = false)

    @Test
    fun `scales 16-bit samples by the track gain`() {
        val processor = configured(trackOnly, gainDb = -6.0206f)

        assertArrayEquals(shortArrayOf(5000, -5000, 0, 16384), processor.process16(10000, -10000, 0, 32767))
    }

    @Test
    fun `makes a track louder and clamps at full scale`() {
        val processor = configured(trackOnly, gainDb = 6.0206f)

        assertArrayEquals(
            shortArrayOf(20000, -20000, 32767, -32768),
            processor.process16(10000, -10000, 20000, -30000),
        )
    }

    @Test
    fun `scales float samples and clamps them to one`() {
        val processor = configured(trackOnly, gainDb = 6.0206f, encoding = C.ENCODING_PCM_FLOAT)

        val out = processor.processFloat(0.25f, -0.25f, 0.8f, -0.9f)

        assertArrayEquals(floatArrayOf(0.5f, -0.5f, 1f, -1f), out, 1e-4f)
    }

    @Test
    fun `off leaves samples as they are`() {
        val processor = configured(ReplayGainSettings(mode = ReplayGainMode.OFF), gainDb = -10f)

        assertArrayEquals(shortArrayOf(123, -456, 32767), processor.process16(123, -456, 32767))
    }

    @Test
    fun `other encodings pass through untouched`() {
        val processor = ReplayGainAudioProcessor()
        processor.configure(AudioFormat(SAMPLE_RATE, 2, C.ENCODING_PCM_24BIT))
        processor.flush()

        assertFalse(processor.isActive)
    }

    @Test
    fun `a new stream changes the gain gradually, then holds it`() {
        val processor = configured(trackOnly, gainDb = 0f)
        processor.stream = ReplayGainStream(ReplayGainInfo(trackGainDb = -20f), albumInOrder = false)

        val rampFrames = SAMPLE_RATE * 30 / 1000
        val out = processor.process16(*ShortArray(rampFrames + 10) { 10000 })

        assertTrue(out.first() in 9000..9999, "first frame ${out.first()}")
        assertTrue(out.toList().zipWithNext().all { (a, b) -> b <= a }, "the gain only goes down")
        assertEquals(1000, out[rampFrames].toInt())
        assertEquals(1000, out.last().toInt())
    }

    @Test
    fun `a flush jumps straight to the new gain`() {
        val processor = configured(trackOnly, gainDb = 0f)
        processor.stream = ReplayGainStream(ReplayGainInfo(trackGainDb = -20f), albumInOrder = false)
        processor.flush()

        assertArrayEquals(shortArrayOf(1000, 1000), processor.process16(10000, 10000))
    }

    @Test
    fun `a settings change applies to the playing track`() {
        val processor = configured(trackOnly, gainDb = -6f)
        processor.settings = trackOnly.copy(preampDb = 6f)
        processor.flush()

        assertEquals(1f, processor.targetFactor, 1e-6f)
    }

    @Test
    fun `auto mode follows the album flag of the stream`() {
        val info = ReplayGainInfo(trackGainDb = -3f, albumGainDb = -9f)
        val processor = ReplayGainAudioProcessor(ReplayGainSettings(clipProtection = false))

        processor.stream = ReplayGainStream(info, albumInOrder = true)
        assertEquals(ReplayGain.dbToLinear(-9f), processor.targetFactor, 1e-6f)
        processor.stream = ReplayGainStream(info, albumInOrder = false)
        assertEquals(ReplayGain.dbToLinear(-3f), processor.targetFactor, 1e-6f)
    }

    @ParameterizedTest(name = "album {0}, previous {1}, next {2} -> {3}")
    @CsvSource(
        nullValues = ["null"],
        value = [
            "1, 1, 1, true",
            "1, 1, null, true",
            "1, null, 1, true",
            "1, 2, 1, true",
            "1, 2, 3, false",
            "1, null, null, false",
            "null, null, null, false",
            "null, 1, 1, false",
        ],
    )
    fun `an album plays in order when a neighbour is from the same album`(
        albumId: Long?,
        previous: Long?,
        next: Long?,
        expected: Boolean,
    ) {
        assertEquals(expected, playsAlbumInOrder(albumId, previous, next))
    }

    private fun configured(
        settings: ReplayGainSettings,
        gainDb: Float,
        encoding: Int = C.ENCODING_PCM_16BIT,
    ): ReplayGainAudioProcessor =
        ReplayGainAudioProcessor(settings).apply {
            stream = ReplayGainStream(ReplayGainInfo(trackGainDb = gainDb), albumInOrder = false)
            configure(AudioFormat(SAMPLE_RATE, 1, encoding))
            flush()
        }

    private fun ReplayGainAudioProcessor.process16(vararg samples: Short): ShortArray {
        val input = buffer(samples.size * Short.SIZE_BYTES)
        samples.forEach(input::putShort)
        input.flip()
        queueInput(input)
        val output = getOutput().order(ByteOrder.nativeOrder())
        return ShortArray(output.remaining() / Short.SIZE_BYTES) { output.getShort() }
    }

    private fun ReplayGainAudioProcessor.processFloat(vararg samples: Float): FloatArray {
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
