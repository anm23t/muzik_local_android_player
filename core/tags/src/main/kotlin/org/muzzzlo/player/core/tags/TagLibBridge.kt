package org.muzzzlo.player.core.tags

import java.io.File

/**
 * Basic audio properties reported by TagLib.
 *
 * @property durationMs length of the audio in milliseconds.
 * @property bitrateKbps average bitrate in kilobits per second.
 * @property sampleRateHz sample rate in hertz.
 * @property channels number of audio channels.
 */
internal data class NativeAudioProperties(
    val durationMs: Int,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val channels: Int,
)

/**
 * Kotlin side of the TagLib JNI bridge: converts between Kotlin types and the raw native calls.
 *
 * This is the smoke-test surface of task 0.3.1. Reading all tags (task 0.3.2) builds on the same conventions:
 * UTF-8 byte arrays across JNI and opening either by path or by file descriptor.
 *
 * Native code can crash the whole process on a malformed file, so callers that read user files
 * wrap each call in [NativeCallGuard.runGuarded]. Calls do file I/O: never run them on the main thread.
 */
internal class TagLibBridge {
    /** Version of the bundled TagLib, for example `2.3.2`. */
    fun version(): String = TagLibNative.nativeVersion().joinToString(".")

    /** Audio properties of the file at [file], or null when TagLib cannot open it. */
    fun readAudioProperties(file: File): NativeAudioProperties? =
        TagLibNative.nativeReadAudioProperties(file.path.encodeToByteArray(), NO_FD)?.toAudioProperties()

    /**
     * Audio properties of an already opened file (on Android, `ParcelFileDescriptor.getFd()`).
     * The descriptor stays open and owned by the caller, but its file offset moves: the native side reads
     * through a `dup()` of it, which shares the offset.
     */
    fun readAudioProperties(fd: Int): NativeAudioProperties? {
        require(fd >= 0) { "Invalid file descriptor: $fd" }
        return TagLibNative.nativeReadAudioProperties(null, fd)?.toAudioProperties()
    }

    /** Title tag of the file at [file], or null when the file cannot be opened or has no title. */
    fun readTitle(file: File): String? =
        TagLibNative.nativeReadTitle(file.path.encodeToByteArray(), NO_FD)?.decodeToString()

    /** Title tag of an already opened file; same descriptor rules as [readAudioProperties]. */
    fun readTitle(fd: Int): String? {
        require(fd >= 0) { "Invalid file descriptor: $fd" }
        return TagLibNative.nativeReadTitle(null, fd)?.decodeToString()
    }

    private fun IntArray.toAudioProperties() =
        NativeAudioProperties(
            durationMs = this[0],
            bitrateKbps = this[1],
            sampleRateHz = this[2],
            channels = this[3],
        )

    private companion object {
        const val NO_FD = -1
    }
}
