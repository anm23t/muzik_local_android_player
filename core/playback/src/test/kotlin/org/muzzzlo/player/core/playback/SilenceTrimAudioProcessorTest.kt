package org.muzzzlo.player.core.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SilenceTrimAudioProcessorTest {
    @Test
    fun `silence at the start of a track is dropped`() {
        val processor = track(enabled = true)

        assertArrayEquals(shortArrayOf(500, 600), processor.process16(0, 0, 10, 33, 500, 600))
        assertEquals(4, processor.skippedFrames)
    }

    @Test
    fun `silence at the end is dropped when the track ends`() {
        val processor = track(enabled = true)

        assertArrayEquals(shortArrayOf(500, 600, 700), processor.process16(500, 600, 700, 0, 0, 5))
        processor.queueEndOfStream()
        assertArrayEquals(shortArrayOf(), processor.drain16())
        assertEquals(3, processor.skippedFrames)
    }

    @Test
    fun `silence inside the track is played, also across buffers`() {
        val processor = track(enabled = true)

        val first = processor.process16(500, 0, 0)
        val second = processor.process16(0, 700)

        assertArrayEquals(shortArrayOf(500), first)
        assertArrayEquals(shortArrayOf(0, 0, 0, 700), second)
        assertEquals(0, processor.skippedFrames)
    }

    @Test
    fun `a new track drops the tail held from the previous one and skips its own start`() {
        val processor = track(enabled = true)
        processor.process16(500, 0, 0)

        processor.startOfTrack()

        assertArrayEquals(shortArrayOf(800), processor.process16(0, 800))
        assertEquals(3, processor.skippedFrames)
    }

    @Test
    fun `a track that is all silence gives no samples`() {
        val processor = track(enabled = true)

        assertArrayEquals(shortArrayOf(), processor.process16(0, 0, 0))
        processor.queueEndOfStream()
        assertEquals(3, processor.skippedFrames)
    }

    @Test
    fun `off passes everything through, also silence held before it was switched off`() {
        val processor = track(enabled = true)
        processor.process16(500, 0)
        processor.enabled = false

        assertArrayEquals(shortArrayOf(0, 0, 0, 9), processor.process16(0, 0, 9))
        assertArrayEquals(shortArrayOf(0, 0, 4), processor.process16(0, 0, 4))
        assertEquals(0, processor.skippedFrames)
    }

    @Test
    fun `silence longer than the held limit is played apart from its last part`() {
        val processor = track(enabled = true)
        val limit = SAMPLE_RATE * SilenceTrimAudioProcessor.MAX_HELD_MS / 1000
        processor.process16(500)

        val played = processor.process16(*ShortArray(limit + 5))
        processor.queueEndOfStream()

        assertEquals(limit, played.size)
        assertEquals(5, processor.skippedFrames)
    }

    @Test
    fun `stereo frame is silent only when both channels are`() {
        val processor = track(enabled = true, channels = 2)

        assertArrayEquals(shortArrayOf(0, 90, 100, 100), processor.process16(0, 0, 0, 90, 100, 100))
        assertEquals(1, processor.skippedFrames)
    }

    @Test
    fun `float samples are judged the same way`() {
        val processor = track(enabled = true, encoding = C.ENCODING_PCM_FLOAT)

        assertArrayEquals(floatArrayOf(0.5f), processor.processFloat(0f, 0.0005f, 0.5f), 0f)
        assertEquals(2, processor.skippedFrames)
    }

    @Test
    fun `a flush resets the dropped frame count`() {
        val processor = track(enabled = true)
        processor.process16(0, 0, 5)

        processor.flush()

        assertEquals(0, processor.skippedFrames)
    }

    @Test
    fun `other encodings pass through untouched`() {
        val processor = SilenceTrimAudioProcessor()
        processor.configure(AudioFormat(SAMPLE_RATE, 2, C.ENCODING_PCM_24BIT))
        processor.flush()

        assertFalse(processor.isActive)
    }

    @Test
    fun `the chain reports the dropped frames to the sink`() {
        val trim = track(enabled = true)
        val chain = TrimChain(trim, ReplayGainAudioProcessor())
        trim.process16(0, 0, 500)

        assertEquals(2, chain.skippedOutputFrameCount)
        assertEquals(listOf(trim::class, ReplayGainAudioProcessor::class), chain.audioProcessors.map { it::class })
    }

    private fun track(
        enabled: Boolean,
        channels: Int = 1,
        encoding: Int = C.ENCODING_PCM_16BIT,
    ): SilenceTrimAudioProcessor =
        SilenceTrimAudioProcessor().apply {
            this.enabled = enabled
            configure(AudioFormat(SAMPLE_RATE, channels, encoding))
            flush()
            startOfTrack()
        }

    private fun SilenceTrimAudioProcessor.process16(vararg samples: Short): ShortArray {
        val input = buffer(samples.size * Short.SIZE_BYTES)
        samples.forEach(input::putShort)
        input.flip()
        queueInput(input)
        return drain16()
    }

    private fun SilenceTrimAudioProcessor.drain16(): ShortArray {
        val output = getOutput().order(ByteOrder.nativeOrder())
        return ShortArray(output.remaining() / Short.SIZE_BYTES) { output.getShort() }
    }

    private fun SilenceTrimAudioProcessor.processFloat(vararg samples: Float): FloatArray {
        val input = buffer(samples.size * Float.SIZE_BYTES)
        samples.forEach(input::putFloat)
        input.flip()
        queueInput(input)
        val output = getOutput().order(ByteOrder.nativeOrder())
        return FloatArray(output.remaining() / Float.SIZE_BYTES) { output.getFloat() }
    }

    private fun buffer(size: Int) = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())

    private companion object {
        const val SAMPLE_RATE = 1000
    }
}
