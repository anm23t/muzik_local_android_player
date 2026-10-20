package org.muzzzlo.player.core.playback

import java.nio.ByteBuffer

/**
 * JNI entry points implemented in `src/main/cpp/alac_jni.cpp` (the Apple ALAC decoder). A decoder is a native
 * handle: 0 means it could not be created, every other handle must be freed with [nativeRelease].
 * Use [AlacDecoder] instead of calling these directly.
 */
internal object AlacNative {
    private const val LIBRARY_NAME = "alac_jni"

    // Error codes of nativeDecode, same values as kError* in alac_jni.cpp.
    const val ERROR_BAD_ARGUMENTS = -1
    const val ERROR_CORRUPT_PACKET = -2

    /** False when the library could not be loaded; ALAC then falls back to the platform decoders. */
    val isAvailable: Boolean by lazy {
        // On Android the library comes from the APK; in JVM unit tests from java.library.path (host build).
        runCatching { System.loadLibrary(LIBRARY_NAME) }.isSuccess
    }

    /** A decoder for the 24-byte magic [cookie] ([AlacConfig.COOKIE_SIZE]); 0 when the cookie is not usable. */
    external fun nativeCreate(cookie: ByteArray): Long

    /**
     * Decodes one packet: [inputSize] bytes of the direct buffer [input] from [inputOffset] into the direct buffer
     * [output] from its start, as interleaved little-endian samples. [output] must hold
     * [AlacConfig.maxOutputSize] bytes. Returns the frames (samples per channel) written, or an `ERROR_*` code.
     */
    external fun nativeDecode(
        handle: Long,
        input: ByteBuffer,
        inputOffset: Int,
        inputSize: Int,
        output: ByteBuffer,
    ): Int

    /** Frees the decoder [handle]; 0 is ignored. */
    external fun nativeRelease(handle: Long)
}
