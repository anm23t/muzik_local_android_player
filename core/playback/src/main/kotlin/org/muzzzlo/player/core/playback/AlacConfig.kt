package org.muzzzlo.player.core.playback

import androidx.media3.common.C
import java.nio.ByteBuffer

/**
 * How an ALAC stream was encoded, read from its magic cookie (`ALACSpecificConfig`, Apple's
 * `ALACMagicCookieDescription.txt`): the cookie comes as the initialization data of the track format.
 */
internal data class AlacConfig(
    /** Samples per channel in a full frame (packet); encoders write 4096. */
    val frameLength: Int,
    /** 16, 20, 24 or 32. */
    val bitDepth: Int,
    val channelCount: Int,
    val sampleRate: Int,
) {
    /** PCM encoding of the decoder output: 20-bit samples come out packed into 24 bits. */
    val pcmEncoding: Int
        get() =
            when (bitDepth) {
                BIT_DEPTH_16 -> C.ENCODING_PCM_16BIT
                BIT_DEPTH_32 -> C.ENCODING_PCM_32BIT
                else -> C.ENCODING_PCM_24BIT
            }

    /** Bytes of one decoded frame (one sample of every channel). */
    val bytesPerFrame: Int
        get() =
            channelCount *
                when (bitDepth) {
                    BIT_DEPTH_16 -> 2
                    BIT_DEPTH_32 -> 4
                    else -> 3
                }

    /** Bytes the decoder writes for a full packet; the output buffer must hold this much. */
    val maxOutputSize: Int get() = frameLength * bytesPerFrame

    companion object {
        /** Size of `ALACSpecificConfig`, the cookie without atom headers. */
        const val COOKIE_SIZE = 24

        /** Same limits as kMaxFrameLength and kMaxChannels in alac_jni.cpp. */
        const val MAX_FRAME_LENGTH = 65536
        const val MAX_CHANNELS = 8

        private const val BIT_DEPTH_16 = 16
        private const val BIT_DEPTH_32 = 32
        private val SUPPORTED_BIT_DEPTHS = setOf(16, 20, 24, 32)

        /** Size of the 'alac' atom header (size, type, version and flags) some muxers leave before the config. */
        private const val ATOM_HEADER_SIZE = 12
        private const val ATOM_TYPE_OFFSET = 4
        private const val ATOM_TYPE_LENGTH = 4

        private const val BYTE_MASK = 0xFF
        private const val OFFSET_FRAME_LENGTH = 0
        private const val OFFSET_BIT_DEPTH = 5
        private const val OFFSET_CHANNELS = 9
        private const val OFFSET_SAMPLE_RATE = 20

        /**
         * The configuration in [initializationData], or null when it is missing or describes a stream the
         * decoder does not take (unknown bit depth, no channels, more than [MAX_CHANNELS], a frame length
         * of 0 or above [MAX_FRAME_LENGTH], no sample rate).
         */
        fun parse(initializationData: List<ByteArray>): AlacConfig? {
            val cookie = initializationData.singleOrNull()?.let(::withoutAtomHeader) ?: return null
            if (cookie.size != COOKIE_SIZE) return null
            val config =
                AlacConfig(
                    frameLength = cookie.readInt(OFFSET_FRAME_LENGTH),
                    bitDepth = cookie.readUnsignedByte(OFFSET_BIT_DEPTH),
                    channelCount = cookie.readUnsignedByte(OFFSET_CHANNELS),
                    sampleRate = cookie.readInt(OFFSET_SAMPLE_RATE),
                )
            return config.takeIf { it.isSupported() }
        }

        /** The 24-byte config, also when the cookie still starts with the 'alac' atom header. */
        fun withoutAtomHeader(cookie: ByteArray): ByteArray =
            if (cookie.size == COOKIE_SIZE + ATOM_HEADER_SIZE && cookie.hasAlacAtomType()) {
                cookie.copyOfRange(ATOM_HEADER_SIZE, cookie.size)
            } else {
                cookie
            }

        private fun AlacConfig.isSupported(): Boolean =
            frameLength in 1..MAX_FRAME_LENGTH &&
                bitDepth in SUPPORTED_BIT_DEPTHS &&
                channelCount in 1..MAX_CHANNELS &&
                sampleRate > 0

        private fun ByteArray.hasAlacAtomType(): Boolean =
            String(this, ATOM_TYPE_OFFSET, ATOM_TYPE_LENGTH, Charsets.US_ASCII) == "alac"

        private fun ByteArray.readUnsignedByte(offset: Int): Int = this[offset].toInt() and BYTE_MASK

        /** Big-endian; a value above [Int.MAX_VALUE] comes out negative and fails the checks. */
        private fun ByteArray.readInt(offset: Int): Int = ByteBuffer.wrap(this).getInt(offset)
    }
}
