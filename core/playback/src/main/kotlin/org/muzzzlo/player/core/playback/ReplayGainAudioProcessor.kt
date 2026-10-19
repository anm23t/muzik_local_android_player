package org.muzzzlo.player.core.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import org.muzzzlo.player.core.model.ReplayGain
import org.muzzzlo.player.core.model.ReplayGainInfo
import org.muzzzlo.player.core.model.ReplayGainSettings
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * What the audio sink needs to know about the track whose samples come next. It rides from the renderer to the
 * sink in `Format.customData`, so it switches exactly where the samples of the next track start.
 *
 * @property albumInOrder the track plays between tracks of its own album, see [playsAlbumInOrder].
 */
internal data class ReplayGainStream(
    val info: ReplayGainInfo,
    val albumInOrder: Boolean,
) {
    companion object {
        /** A track without ReplayGain values, outside of an album. */
        val NONE = ReplayGainStream(ReplayGainInfo.NONE, albumInOrder = false)
    }
}

/**
 * True when the track plays as part of its album: the track before or after it in play order is from the same
 * album. Decides [org.muzzzlo.player.core.model.ReplayGainMode.AUTO].
 */
internal fun playsAlbumInOrder(
    albumId: Long?,
    previousAlbumId: Long?,
    nextAlbumId: Long?,
): Boolean = albumId != null && (albumId == previousAlbumId || albumId == nextAlbumId)

/**
 * Multiplies 16-bit and float PCM samples by the ReplayGain factor of the current track. Unlike the player volume
 * it can also make a track louder. Samples that would go over full scale are clamped; with clip protection on and a
 * peak in the tags this does not happen. A change of the factor is spread over [RAMP_MS] to avoid a click.
 *
 * [settings] and [stream] may be set from any thread; the samples are processed on the playback thread.
 */
@UnstableApi
internal class ReplayGainAudioProcessor(
    settings: ReplayGainSettings = ReplayGainSettings(),
) : BaseAudioProcessor() {
    @Volatile var settings: ReplayGainSettings = settings
        set(value) {
            field = value
            updateTarget()
        }

    @Volatile var stream: ReplayGainStream = ReplayGainStream.NONE
        set(value) {
            field = value
            updateTarget()
        }

    /** The factor the samples are moving towards. */
    @Volatile var targetFactor: Float = factorOf(settings, stream)
        private set

    private var currentFactor = targetFactor
    private var rampTarget = targetFactor
    private var rampStep = 0f
    private var rampFrames = 1

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat =
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT -> inputAudioFormat
            // Other encodings reach the sink only when float output is on; they pass through untouched.
            else -> AudioFormat.NOT_SET
        }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val output = replaceOutputBuffer(remaining)
        val channels = inputAudioFormat.channelCount
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            while (inputBuffer.remaining() >= channels * Short.SIZE_BYTES) {
                val factor = nextFactor()
                repeat(channels) { output.putShort(scale(inputBuffer.getShort(), factor)) }
            }
        } else {
            while (inputBuffer.remaining() >= channels * Float.SIZE_BYTES) {
                val factor = nextFactor()
                repeat(channels) { output.putFloat((inputBuffer.getFloat() * factor).coerceIn(-1f, 1f)) }
            }
        }
        // A partial frame (never sent by the sink) is dropped rather than left in the input.
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onFlush() {
        // A flush comes with a seek or a new configuration: there is no sound to blend with, jump to the target.
        rampFrames = maxOf(1, inputAudioFormat.sampleRate * RAMP_MS / MS_PER_SECOND)
        jumpToTarget()
    }

    override fun onReset() = jumpToTarget()

    private fun jumpToTarget() {
        currentFactor = targetFactor
        rampTarget = currentFactor
        rampStep = 0f
    }

    private fun updateTarget() {
        targetFactor = factorOf(settings, stream)
    }

    private fun nextFactor(): Float {
        val target = targetFactor
        if (currentFactor == target) return target
        if (target != rampTarget) {
            rampTarget = target
            rampStep = (target - currentFactor) / rampFrames
        }
        currentFactor += rampStep
        val reached = rampStep == 0f || (rampStep > 0f) == (currentFactor >= target)
        if (reached) currentFactor = target
        return currentFactor
    }

    private companion object {
        const val RAMP_MS = 30
        const val MS_PER_SECOND = 1000

        fun factorOf(
            settings: ReplayGainSettings,
            stream: ReplayGainStream,
        ): Float = ReplayGain.factor(settings, stream.info, stream.albumInOrder)

        fun scale(
            sample: Short,
            factor: Float,
        ): Short = (sample * factor).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
}
