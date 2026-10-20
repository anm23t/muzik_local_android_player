package org.muzzzlo.player.core.playback

import androidx.media3.common.util.UnstableApi
import java.io.Closeable
import java.nio.ByteBuffer

/**
 * Decodes the packets of one ALAC stream with the native Apple decoder. Packets are independent, so there is
 * nothing to reset after a seek. Not thread-safe: one thread decodes, then [close] frees the native decoder.
 */
@UnstableApi
internal class AlacStreamDecoder private constructor(
    val config: AlacConfig,
    private var handle: Long,
) : Closeable {
    /**
     * Decodes the remaining bytes of the direct buffer [packet] into the direct buffer [output] from its start
     * and sets the limit of [output] to the end of the samples. [output] must hold [AlacConfig.maxOutputSize]
     * bytes. Returns the decoded frames (samples per channel).
     */
    fun decode(
        packet: ByteBuffer,
        output: ByteBuffer,
    ): Int {
        check(handle != 0L) { "The decoder is closed" }
        val frames = AlacNative.nativeDecode(handle, packet, packet.position(), packet.remaining(), output)
        if (frames < 0) throw AlacDecoderException(errorMessage(frames))
        output.position(0)
        output.limit(frames * config.bytesPerFrame)
        return frames
    }

    override fun close() {
        AlacNative.nativeRelease(handle)
        handle = 0L
    }

    private fun errorMessage(code: Int): String =
        when (code) {
            AlacNative.ERROR_CORRUPT_PACKET -> "Corrupt ALAC packet"
            else -> "ALAC decoder rejected the buffers ($code)"
        }

    companion object {
        /** A decoder for the stream described by [initializationData] (the magic cookie). */
        fun create(initializationData: List<ByteArray>): AlacStreamDecoder {
            val config = AlacConfig.parse(initializationData) ?: throw AlacDecoderException("Unsupported ALAC cookie")
            check(AlacNative.isAvailable) { "libalac_jni is not loaded" }
            val cookie = AlacConfig.withoutAtomHeader(initializationData.single())
            val handle = AlacNative.nativeCreate(cookie)
            if (handle == 0L) throw AlacDecoderException("The ALAC decoder refused the cookie")
            return AlacStreamDecoder(config, handle)
        }
    }
}
