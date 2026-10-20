package org.muzzzlo.player.core.playback

import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Drops digital silence at the start and at the end of a track, and only there: silence between sounds stays, so a
 * pause inside a song or a hidden track is played as it is.
 *
 * The start of a track is announced by [startOfTrack]. Silence after it is dropped until the first sound. Silence is
 * held back until a sound follows (then it was in the middle of the track and is played) or the track ends (then it
 * is the tail and is dropped). At most [MAX_HELD_MS] of silence is held: a longer gap is played, apart from its last
 * [MAX_HELD_MS]. Silence is a sample not louder than −60 dBFS.
 *
 * The frames dropped are counted in [skippedFrames], which the audio sink adds to the playback position
 * ([TrimChain]); otherwise the position would fall behind the media time by every track.
 *
 * [enabled] may be set from any thread; the samples are processed on the playback thread.
 */
@UnstableApi
internal class SilenceTrimAudioProcessor : BaseAudioProcessor() {
    @Volatile var enabled: Boolean = false

    /** Frames dropped since the last flush. */
    @Volatile var skippedFrames: Long = 0
        private set

    private var leading = false
    private var held = ByteArray(0)
    private var heldBytes = 0
    private var maxHeldBytes = 0

    /** The samples that come next belong to a new track: drop what is held of the previous one, skip its start. */
    fun startOfTrack() {
        dropHeld()
        leading = enabled
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat =
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT -> inputAudioFormat
            // Other encodings reach the sink only when float output is on; they pass through untouched.
            else -> AudioFormat.NOT_SET
        }

    override fun onFlush() {
        heldBytes = 0
        skippedFrames = 0
        maxHeldBytes = inputAudioFormat.bytesPerFrame * (inputAudioFormat.sampleRate * MAX_HELD_MS / MS_PER_SECOND)
        if (held.size < maxHeldBytes) held = ByteArray(maxHeldBytes)
    }

    override fun onReset() {
        held = ByteArray(0)
        heldBytes = 0
        skippedFrames = 0
        leading = false
    }

    override fun onQueueEndOfStream() = dropHeld()

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val frameBytes = inputAudioFormat.bytesPerFrame
        val output = replaceOutputBuffer(remaining + heldBytes)
        if (!enabled) {
            leading = false
            flushHeld(output)
            output.put(inputBuffer)
            output.flip()
            return
        }
        while (inputBuffer.remaining() >= frameBytes) {
            val start = inputBuffer.position()
            val silent = isSilent(inputBuffer, start)
            when {
                silent && leading -> skippedFrames++
                silent -> holdFrame(inputBuffer, start, frameBytes, output)
                else -> {
                    leading = false
                    flushHeld(output)
                    val limit = inputBuffer.limit()
                    inputBuffer.limit(start + frameBytes)
                    output.put(inputBuffer)
                    inputBuffer.limit(limit)
                    continue
                }
            }
            inputBuffer.position(start + frameBytes)
        }
        // A partial frame (never sent by the sink) is dropped rather than left in the input.
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    /** Keeps the silent frame at [start] back; the held silence is played when it grows past [maxHeldBytes]. */
    private fun holdFrame(
        input: ByteBuffer,
        start: Int,
        frameBytes: Int,
        output: ByteBuffer,
    ) {
        if (heldBytes + frameBytes > maxHeldBytes) flushHeld(output)
        if (frameBytes > maxHeldBytes) return
        input.get(start, held, heldBytes, frameBytes)
        heldBytes += frameBytes
    }

    private fun flushHeld(output: ByteBuffer) {
        if (heldBytes == 0) return
        output.put(held, 0, heldBytes)
        heldBytes = 0
    }

    private fun dropHeld() {
        val frameBytes = inputAudioFormat.bytesPerFrame
        if (heldBytes > 0 && frameBytes > 0) skippedFrames += heldBytes / frameBytes
        heldBytes = 0
    }

    /** True when every channel of the frame at [position] is silent. */
    private fun isSilent(
        buffer: ByteBuffer,
        position: Int,
    ): Boolean {
        val float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        repeat(inputAudioFormat.channelCount) { channel ->
            val quiet =
                if (float) {
                    abs(buffer.getFloat(position + channel * Float.SIZE_BYTES)) <= SILENCE_FLOAT
                } else {
                    abs(buffer.getShort(position + channel * Short.SIZE_BYTES).toInt()) <= SILENCE_16_BIT
                }
            if (!quiet) return false
        }
        return true
    }

    companion object {
        /** The longest silence held back at the end of a track, in milliseconds. */
        const val MAX_HELD_MS = 10_000

        private const val MS_PER_SECOND = 1000

        /** −60 dBFS. */
        private const val SILENCE_FLOAT = 0.001f
        private const val SILENCE_16_BIT = 33
    }
}

/**
 * The processors of the audio sink in the order they run: silence trimming, then ReplayGain. Playback speed is not
 * supported. The sink adds the frames dropped by [trim] to the playback position.
 */
@UnstableApi
internal class TrimChain(
    private val trim: SilenceTrimAudioProcessor,
    private val replayGain: ReplayGainAudioProcessor,
) : DefaultAudioSink.AudioProcessorChain {
    override fun getAudioProcessors(): Array<AudioProcessor> = arrayOf(trim, replayGain)

    override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters =
        PlaybackParameters.DEFAULT

    override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean = false

    override fun getMediaDuration(playoutDuration: Long): Long = playoutDuration

    override fun getSkippedOutputFrameCount(): Long = trim.skippedFrames
}
