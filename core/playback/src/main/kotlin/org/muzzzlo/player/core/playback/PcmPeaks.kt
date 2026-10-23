package org.muzzzlo.player.core.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import org.muzzzlo.player.core.model.PeakCollector
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Feeds decoded PCM into a [PeakCollector]: for every frame the largest absolute sample of its channels, scaled so
 * that full scale is 1. [encoding] is a Media3 `C.ENCODING_PCM_*` value (the same numbers as the platform
 * `AudioFormat`), samples are interleaved little-endian as decoders write them.
 */
@UnstableApi
internal class PcmPeaks(
    private val collector: PeakCollector,
    private val encoding: Int,
    private val channelCount: Int,
) {
    private val bytesPerSample =
        when (encoding) {
            C.ENCODING_PCM_16BIT -> SHORT_BYTES
            C.ENCODING_PCM_24BIT -> INT24_BYTES
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> INT_BYTES
            else -> throw IllegalArgumentException("Unsupported PCM encoding $encoding")
        }

    init {
        require(channelCount > 0) { "No channels" }
    }

    /** Samples of one bulk read: 16-bit and float PCM are read in chunks instead of one call per sample. */
    private var chunk16 = ShortArray(0)
    private var chunkFloat = FloatArray(0)

    /** Reads all whole frames of [buffer] from its position; a partial frame at the end is skipped. */
    fun add(buffer: ByteBuffer) {
        val data = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val frames = data.remaining() / (bytesPerSample * channelCount)
        when (encoding) {
            C.ENCODING_PCM_16BIT -> add16(data, frames)
            C.ENCODING_PCM_FLOAT -> addFloat(data, frames)
            else -> addEach(data)
        }
    }

    private fun add16(
        data: ByteBuffer,
        frames: Int,
    ) {
        val samples = frames * channelCount
        if (chunk16.size < samples) chunk16 = ShortArray(samples)
        data.asShortBuffer().get(chunk16, 0, samples)
        var i = 0
        repeat(frames) {
            var peak = 0
            repeat(channelCount) {
                val v = chunk16[i++].toInt()
                peak = maxOf(peak, if (v < 0) -v else v)
            }
            collector.add(peak / SHORT_SCALE)
        }
    }

    private fun addFloat(
        data: ByteBuffer,
        frames: Int,
    ) {
        val samples = frames * channelCount
        if (chunkFloat.size < samples) chunkFloat = FloatArray(samples)
        data.asFloatBuffer().get(chunkFloat, 0, samples)
        var i = 0
        repeat(frames) {
            var peak = 0f
            repeat(channelCount) { peak = maxOf(peak, abs(chunkFloat[i++])) }
            collector.add(peak)
        }
    }

    private fun addEach(data: ByteBuffer) {
        val frameBytes = bytesPerSample * channelCount
        while (data.remaining() >= frameBytes) {
            var peak = 0f
            repeat(channelCount) { peak = maxOf(peak, abs(sample(data))) }
            collector.add(peak)
        }
    }

    private fun sample(data: ByteBuffer): Float =
        when (encoding) {
            C.ENCODING_PCM_16BIT -> data.getShort() / SHORT_SCALE
            C.ENCODING_PCM_24BIT -> {
                val low = data.get().toInt() and BYTE_MASK
                val mid = data.get().toInt() and BYTE_MASK
                val high = data.get().toInt()
                ((high shl SHIFT_16) or (mid shl SHIFT_8) or low) / INT24_SCALE
            }
            C.ENCODING_PCM_32BIT -> data.getInt() / INT_SCALE
            else -> data.getFloat()
        }

    private companion object {
        const val SHORT_BYTES = 2
        const val INT24_BYTES = 3
        const val INT_BYTES = 4
        const val BYTE_MASK = 0xFF
        const val SHIFT_8 = 8
        const val SHIFT_16 = 16
        const val SHORT_SCALE = 32768f
        const val INT24_SCALE = 8388608f
        const val INT_SCALE = 2147483648f
    }
}
