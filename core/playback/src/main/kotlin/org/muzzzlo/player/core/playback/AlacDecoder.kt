package org.muzzzlo.player.core.playback

import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderException
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoder
import androidx.media3.decoder.SimpleDecoderOutputBuffer

/** An ALAC stream could not be decoded: unsupported cookie or corrupt packet. */
@UnstableApi
internal class AlacDecoderException(
    message: String,
    cause: Throwable? = null,
) : DecoderException(message, cause)

/**
 * Media3 decoder for ALAC: one input buffer is one packet of the MP4 track, one output buffer its PCM samples
 * ([AlacConfig.pcmEncoding]). Decoding runs on the decoder thread of [SimpleDecoder].
 */
@UnstableApi
internal class AlacDecoder private constructor(
    format: Format,
    private val stream: AlacStreamDecoder,
) : SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, AlacDecoderException>(
        arrayOfNulls(BUFFER_COUNT),
        arrayOfNulls(BUFFER_COUNT),
    ) {
    val config: AlacConfig get() = stream.config

    init {
        val maxInputSize = format.maxInputSize.takeIf { it != Format.NO_VALUE } ?: config.maxOutputSize
        setInitialInputBufferSize(maxInputSize)
    }

    override fun getName(): String = NAME

    // The native decoder reads the packet by address, so input buffers must be direct.
    override fun createInputBuffer(): DecoderInputBuffer =
        DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT)

    override fun createOutputBuffer(): SimpleDecoderOutputBuffer = SimpleDecoderOutputBuffer(::releaseOutputBuffer)

    override fun createUnexpectedDecodeException(error: Throwable): AlacDecoderException =
        AlacDecoderException("Unexpected ALAC decode error", error)

    override fun decode(
        inputBuffer: DecoderInputBuffer,
        outputBuffer: SimpleDecoderOutputBuffer,
        reset: Boolean,
    ): AlacDecoderException? {
        val packet = checkNotNull(inputBuffer.data)
        val output = outputBuffer.init(inputBuffer.timeUs, config.maxOutputSize)
        return try {
            stream.decode(packet, output)
            null
        } catch (e: AlacDecoderException) {
            e
        }
    }

    override fun release() {
        super.release()
        stream.close()
    }

    companion object {
        private const val NAME = "apple-alac"
        private const val BUFFER_COUNT = 16

        /**
         * A decoder for [format]. The native decoder is created first: [SimpleDecoder] starts its thread in the
         * constructor, and a failure after that would leave the thread waiting forever.
         */
        fun create(format: Format): AlacDecoder =
            AlacDecoder(format, AlacStreamDecoder.create(format.initializationData))
    }
}
