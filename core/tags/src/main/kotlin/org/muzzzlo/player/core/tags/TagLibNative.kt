package org.muzzzlo.player.core.tags

/**
 * JNI entry points implemented in `src/main/cpp/taglib_jni.cpp`.
 *
 * Strings cross the boundary as UTF-8 byte arrays. A file is opened by path bytes or, when `fd >= 0`,
 * by a file descriptor that stays owned by the caller (the native side reads from a duplicate). With a descriptor,
 * `nameUtf8` is the file name used to pick the format by extension; null makes TagLib guess from the content.
 * Use [TagLibBridge] instead of calling these directly.
 */
internal object TagLibNative {
    private const val LIBRARY_NAME = "taglib_jni"

    // Container codes, same values as kContainer* in taglib_jni.cpp.
    const val CONTAINER_UNKNOWN = 0
    const val CONTAINER_MP3 = 1
    const val CONTAINER_FLAC = 2
    const val CONTAINER_MP4 = 3
    const val CONTAINER_OGG_VORBIS = 4
    const val CONTAINER_OPUS = 5

    // Indexes in the info array of nativeReadTags, same order as InfoIndex in taglib_jni.cpp.
    const val INFO_CONTAINER = 0
    const val INFO_ID3V2_VERSION = 1
    const val INFO_PICTURE_COUNT = 2
    const val INFO_HAS_AUDIO = 3
    const val INFO_LENGTH_MS = 4
    const val INFO_BITRATE_KBPS = 5
    const val INFO_SAMPLE_RATE_HZ = 6
    const val INFO_CHANNELS = 7

    init {
        // On Android the library comes from the APK; in JVM unit tests from java.library.path (host build).
        System.loadLibrary(LIBRARY_NAME)
    }

    /** `[major, minor, patch]` of the TagLib sources the library was built from. */
    external fun nativeVersion(): IntArray

    /**
     * `[info: IntArray, properties: Array<ByteArray>]`, or null when TagLib cannot open the file.
     * `info` is indexed by the `INFO_*` constants; `properties` is `[key, value, key, value, ...]` in UTF-8,
     * with a key repeated once per value.
     */
    external fun nativeReadTags(
        pathUtf8: ByteArray?,
        fd: Int,
        nameUtf8: ByteArray?,
        readAudioProperties: Boolean,
    ): Array<Any>?

    /**
     * `[data: ByteArray, mimeTypeUtf8: ByteArray, pictureType: IntArray(1)]` of the front cover, or of the first
     * picture when there is no front cover; null when the file cannot be opened or has no pictures.
     */
    external fun nativeReadCover(
        pathUtf8: ByteArray?,
        fd: Int,
        nameUtf8: ByteArray?,
    ): Array<Any>?
}
