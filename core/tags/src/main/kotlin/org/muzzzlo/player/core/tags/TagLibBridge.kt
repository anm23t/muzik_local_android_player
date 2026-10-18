package org.muzzzlo.player.core.tags

import org.muzzzlo.player.core.model.AudioFormat
import org.muzzzlo.player.core.model.AudioInfo
import org.muzzzlo.player.core.model.EmbeddedPicture
import org.muzzzlo.player.core.model.TagSource

/**
 * Raw result of one native read, before the property map is turned into tags.
 *
 * @property format container format, null for a file TagLib opened but the app does not support.
 * @property id3v2Version major ID3v2 version of an MP3 file, null when it has no ID3v2 tag.
 * @property pictureCount number of embedded pictures.
 * @property audio audio properties, null when not requested or when TagLib found no audio stream header.
 * @property properties TagLib property map: upper-case keys (`TITLE`, `ARTIST`, ...) to their values in file order.
 */
internal data class NativeTags(
    val format: AudioFormat?,
    val id3v2Version: Int?,
    val pictureCount: Int,
    val audio: AudioInfo?,
    val properties: Map<String, List<String>>,
)

/**
 * Kotlin side of the TagLib JNI bridge: converts between Kotlin types and the raw native calls.
 *
 * Native code can crash the whole process on a malformed file, so callers that read user files
 * wrap each call in [NativeCallGuard.runGuarded]. Calls do file I/O: never run them on the main thread.
 *
 * A [TagSource.Descriptor] stays open and owned by the caller, but its file offset moves: the native side reads
 * through a `dup()` of it, which shares the offset.
 */
internal class TagLibBridge {
    /** Version of the bundled TagLib, for example `2.3.2`. */
    fun version(): String = TagLibNative.nativeVersion().joinToString(".")

    /** Tags of [source], or null when TagLib cannot open it. */
    fun readTags(
        source: TagSource,
        withAudioInfo: Boolean,
    ): NativeTags? {
        val args = source.toNativeArguments()
        val raw = TagLibNative.nativeReadTags(args.path, args.fd, args.name, withAudioInfo) ?: return null
        return toNativeTags(info = raw[0] as IntArray, flatProperties = raw[1] as Array<*>)
    }

    /** The front cover of [source] (or its first picture), null when there is none or the file cannot be opened. */
    fun readCover(source: TagSource): EmbeddedPicture? {
        val args = source.toNativeArguments()
        val raw = TagLibNative.nativeReadCover(args.path, args.fd, args.name) ?: return null
        return EmbeddedPicture(
            data = raw[0] as ByteArray,
            mimeType = (raw[1] as ByteArray).decodeToString(),
            pictureType = (raw[2] as IntArray)[0],
        )
    }

    /** Path, descriptor and file name in the form the native calls take. */
    private class NativeArguments(val path: ByteArray?, val fd: Int, val name: ByteArray?)

    private fun TagSource.toNativeArguments(): NativeArguments =
        when (this) {
            is TagSource.FilePath -> NativeArguments(path.encodeToByteArray(), NO_FD, null)
            is TagSource.Descriptor -> {
                require(fd >= 0) { "Invalid file descriptor: $fd" }
                NativeArguments(null, fd, fileName?.encodeToByteArray())
            }
        }

    private fun toNativeTags(
        info: IntArray,
        flatProperties: Array<*>,
    ): NativeTags {
        val properties = LinkedHashMap<String, MutableList<String>>()
        for (i in flatProperties.indices step 2) {
            val key = (flatProperties[i] as ByteArray).decodeToString()
            val value = (flatProperties[i + 1] as ByteArray).decodeToString()
            properties.getOrPut(key) { ArrayList() } += value
        }
        val audio =
            // A sample rate of 0 means TagLib found no usable audio stream header.
            if (info[TagLibNative.INFO_HAS_AUDIO] != 0 && info[TagLibNative.INFO_SAMPLE_RATE_HZ] > 0) {
                AudioInfo(
                    durationMs = info[TagLibNative.INFO_LENGTH_MS].toLong(),
                    bitrateKbps = info[TagLibNative.INFO_BITRATE_KBPS],
                    sampleRateHz = info[TagLibNative.INFO_SAMPLE_RATE_HZ],
                    channels = info[TagLibNative.INFO_CHANNELS],
                )
            } else {
                null
            }
        return NativeTags(
            format = formatOf(info[TagLibNative.INFO_CONTAINER]),
            id3v2Version = info[TagLibNative.INFO_ID3V2_VERSION].takeIf { it > 0 },
            pictureCount = info[TagLibNative.INFO_PICTURE_COUNT],
            audio = audio,
            properties = properties,
        )
    }

    private fun formatOf(container: Int): AudioFormat? =
        when (container) {
            TagLibNative.CONTAINER_MP3 -> AudioFormat.MP3
            TagLibNative.CONTAINER_FLAC -> AudioFormat.FLAC
            TagLibNative.CONTAINER_MP4 -> AudioFormat.M4A
            TagLibNative.CONTAINER_OGG_VORBIS -> AudioFormat.OGG_VORBIS
            TagLibNative.CONTAINER_OPUS -> AudioFormat.OPUS
            else -> null
        }

    private companion object {
        const val NO_FD = -1
    }
}
