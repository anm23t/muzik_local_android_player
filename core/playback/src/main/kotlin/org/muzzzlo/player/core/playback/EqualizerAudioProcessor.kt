package org.muzzzlo.player.core.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import org.muzzzlo.player.core.model.Biquad
import org.muzzzlo.player.core.model.Equalizer
import org.muzzzlo.player.core.model.EqualizerSettings
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Runs the 10-band equalizer ([Equalizer], task 1.2.8) on 16-bit and float PCM, after ReplayGain in the chain of each
 * deck. The filters are biquads in transposed direct form II with their state per channel; a flush (seek, new
 * track) clears the state. A neutral equalizer copies the samples unchanged.
 *
 * [settings] and [clipProtection] may be set from any thread; the filters are rebuilt before the next buffer.
 */
@UnstableApi
internal class EqualizerAudioProcessor(
    settings: EqualizerSettings = EqualizerSettings(),
    clipProtection: Boolean = true,
) : BaseAudioProcessor() {
    @Volatile var settings: EqualizerSettings = settings
        set(value) {
            field = value
            dirty = true
        }

    /** The ReplayGain clip protection: the output is lowered by the highest point of the curve. */
    @Volatile var clipProtection: Boolean = clipProtection
        set(value) {
            field = value
            dirty = true
        }

    @Volatile private var dirty = true

    private var sections: Array<Biquad> = emptyArray()
    private var factor = 1.0
    private var neutral = true

    /** Two state values per section and channel: `z1, z2` of section `s` of channel `c` at `(c * sections + s) * 2`. */
    private var state = DoubleArray(0)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        dirty = true
        return when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT -> inputAudioFormat
            else -> AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        if (dirty) rebuild()
        val output = replaceOutputBuffer(remaining)
        if (neutral) {
            output.put(inputBuffer)
        } else {
            filter(inputBuffer, output)
        }
        output.flip()
    }

    override fun onFlush() = state.fill(0.0)

    override fun onReset() {
        state = DoubleArray(0)
        dirty = true
    }

    private fun rebuild() {
        dirty = false
        val current = settings
        val rate = inputAudioFormat.sampleRate
        val wasNeutral = neutral
        neutral = current.isNeutral || rate <= 0
        if (neutral) return
        sections = Equalizer.filters(current, rate).filter { it != Biquad.IDENTITY }.toTypedArray()
        factor = Equalizer.outputFactor(current, rate, clipProtection)
        val size = inputAudioFormat.channelCount * sections.size * 2
        // Filters that were already running keep their state, so dragging a slider does not click.
        if (wasNeutral || state.size != size) state = DoubleArray(size)
    }

    private fun filter(
        input: ByteBuffer,
        output: ByteBuffer,
    ) {
        val channels = inputAudioFormat.channelCount
        val is16Bit = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
        val sampleBytes = if (is16Bit) Short.SIZE_BYTES else Float.SIZE_BYTES
        while (input.remaining() >= channels * sampleBytes) {
            for (channel in 0 until channels) {
                if (is16Bit) {
                    val y = process(input.getShort() / SHORT_SCALE, channel) * SHORT_SCALE
                    output.putShort(y.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
                } else {
                    output.putFloat(process(input.getFloat().toDouble(), channel).toFloat().coerceIn(-1f, 1f))
                }
            }
        }
        input.position(input.limit())
    }

    private fun process(
        sample: Double,
        channel: Int,
    ): Double {
        var x = sample
        var at = channel * sections.size * 2
        for (s in sections) {
            val y = s.b0 * x + state[at]
            state[at] = s.b1 * x - s.a1 * y + state[at + 1]
            state[at + 1] = s.b2 * x - s.a2 * y
            x = y
            at += 2
        }
        return x * factor
    }

    private companion object {
        const val SHORT_SCALE = 32768.0
    }
}
