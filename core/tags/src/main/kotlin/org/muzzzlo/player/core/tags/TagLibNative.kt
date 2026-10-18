package org.muzzzlo.player.core.tags

/**
 * JNI entry points implemented in `src/main/cpp/taglib_jni.cpp`.
 *
 * Strings cross the boundary as UTF-8 byte arrays. A file is opened by [path bytes][nativeReadTitle] or, when
 * `fd >= 0`, by a file descriptor that stays owned by the caller (the native side reads from a duplicate).
 * Use [TagLibBridge] instead of calling these directly.
 */
internal object TagLibNative {
    private const val LIBRARY_NAME = "taglib_jni"

    init {
        // On Android the library comes from the APK; in JVM unit tests from java.library.path (host build).
        System.loadLibrary(LIBRARY_NAME)
    }

    /** `[major, minor, patch]` of the TagLib sources the library was built from. */
    external fun nativeVersion(): IntArray

    /** `[lengthMs, bitrateKbps, sampleRateHz, channels]`, or null when TagLib cannot open the file. */
    external fun nativeReadAudioProperties(
        pathUtf8: ByteArray?,
        fd: Int,
    ): IntArray?

    /** The title as UTF-8 bytes, or null when the file cannot be opened or has no title. */
    external fun nativeReadTitle(
        pathUtf8: ByteArray?,
        fd: Int,
    ): ByteArray?
}
