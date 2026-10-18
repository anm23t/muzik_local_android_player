package org.muzzzlo.player.core.model

/**
 * Where a [TagReader] reads a file from.
 *
 * @property key stable single-line identifier of the file, for example its content URI. A file that once crashed
 *   the native reader is remembered by this key and skipped afterwards.
 */
sealed interface TagSource {
    val key: String

    /** A file opened by its path (the app's own files, tests). */
    data class FilePath(val path: String) : TagSource {
        override val key: String get() = path
    }

    /**
     * An already opened file descriptor, for example `ParcelFileDescriptor.getFd()` of a `content://` URI.
     * The caller keeps owning it and closes it after the call; its file offset moves during the read.
     *
     * @property fileName the file's name (MediaStore `DISPLAY_NAME`, `song.mp3`). The format is picked by its
     *   extension, as for a path; without it the format is guessed from the content, which rejects some valid
     *   files (an MP3 with a second ID3v2 tag after its first frame).
     */
    data class Descriptor(
        val fd: Int,
        override val key: String,
        val fileName: String? = null,
    ) : TagSource
}

/** Result of [TagReader.readTags]. */
sealed interface TagReadResult {
    /** The file was read. */
    data class Success(val fileTags: FileTags) : TagReadResult

    /** The file is not a supported format or is broken in a way the tag library reports. */
    data object Unreadable : TagReadResult

    /** The file crashed the native reader earlier and is skipped until the user retries it. */
    data object Quarantined : TagReadResult
}

/**
 * Reads tags, covers and lyrics from audio files. Implemented in `core:tags`.
 *
 * Calls do file I/O and switch to the I/O dispatcher themselves.
 */
interface TagReader {
    /**
     * Reads all tags of [source]; audio properties only when [withAudioInfo] is true, because they cost extra time.
     */
    suspend fun readTags(
        source: TagSource,
        withAudioInfo: Boolean = true,
    ): TagReadResult

    /**
     * The cover of [source]: the front cover when there is one, otherwise the first picture.
     * Null when the file has no pictures, cannot be read or is quarantined.
     */
    suspend fun readCover(source: TagSource): EmbeddedPicture?
}
