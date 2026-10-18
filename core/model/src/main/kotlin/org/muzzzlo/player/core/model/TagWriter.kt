package org.muzzzlo.player.core.model

/**
 * The file a [TagWriter] changes.
 *
 * @property key stable single-line identifier of the file, the same as [TagSource.key] when the file is read.
 */
sealed interface TagWriteTarget {
    val key: String

    /** File name with its extension; the tag library picks the format by it. */
    val fileName: String

    /** A file opened by its path (the app's own files, tests). */
    data class FilePath(val path: String) : TagWriteTarget {
        override val key: String get() = path
        override val fileName: String get() = path.substringAfterLast('/')
    }

    /**
     * A MediaStore file.
     *
     * @property uri its `content://` URI.
     * @property fileName MediaStore `DISPLAY_NAME`, for example `song.mp3`.
     */
    data class ContentUri(val uri: String, override val fileName: String) : TagWriteTarget {
        override val key: String get() = uri
    }
}

/** What happens to the cover in a [TagEdit]. */
sealed interface CoverEdit {
    /** The pictures stay as they are. */
    data object Keep : CoverEdit

    /** Every embedded picture is removed, so no other picture shows up as the cover afterwards. */
    data object Remove : CoverEdit

    /**
     * The front cover (or, without one, the first picture) is replaced by [picture]; other pictures stay.
     * A file without pictures gets [picture] as its front cover.
     */
    data class Replace(val picture: EmbeddedPicture) : CoverEdit
}

/**
 * The tags a file should have after a write.
 *
 * It holds the full wanted state of the fields the app edits. A field equal to what the file already has is not
 * rewritten, so its stored form stays (a full date `2024-05-01`, the `TOTALTRACKS` spelling, several lyrics).
 * Everything else in the file (unknown frames and fields, ReplayGain, other pictures) is kept as it is.
 * Values are trimmed and blank ones dropped, as when reading: an empty field removes it from the file.
 *
 * Artists are written as separate values; ID3v2.3 has no such values and gets one string joined with `; `
 * (ARCHITECTURE.md, section 5). The same goes for album artists and genres. A track or disc total without
 * its number is dropped in ID3v2 and MP4, which store both as one `3/10` value.
 */
data class TagEdit(
    val title: String? = null,
    val artists: List<String> = emptyList(),
    val album: String? = null,
    val albumArtists: List<String> = emptyList(),
    val trackNumber: Int? = null,
    val trackTotal: Int? = null,
    val discNumber: Int? = null,
    val discTotal: Int? = null,
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    val lyrics: String? = null,
    val cover: CoverEdit = CoverEdit.Keep,
) {
    companion object {
        /** An edit that keeps every field of [tags]: the starting point for the tag editor. */
        fun from(tags: TrackTags): TagEdit =
            TagEdit(
                title = tags.title,
                artists = tags.artists,
                album = tags.album,
                albumArtists = tags.albumArtists,
                trackNumber = tags.trackNumber,
                trackTotal = tags.trackTotal,
                discNumber = tags.discNumber,
                discTotal = tags.discTotal,
                year = tags.year,
                genres = tags.genres,
                lyrics = tags.lyrics,
            )
    }
}

/** Result of [TagWriter.writeTags]. Unless it says otherwise, the original file is unchanged. */
sealed interface TagWriteResult {
    /** The new tags are in the file and were checked by reading it back. */
    data object Success : TagWriteResult

    /** The app may not change the file yet: the user has to allow it first (system dialog or permission). */
    data object NeedsPermission : TagWriteResult

    /** The app's private storage has less than [requiredBytes] free for the working and the backup copy. */
    data class NotEnoughSpace(val requiredBytes: Long) : TagWriteResult

    /** The file is missing, not a supported format or broken. */
    data object Unreadable : TagWriteResult

    /** The file crashed the native tag library before and is not touched. */
    data object Quarantined : TagWriteResult

    /** The tag library could not save the new tags into the working copy. */
    data object WriteFailed : TagWriteResult

    /**
     * The working copy did not pass the check: [reason] says what differed (tags, cover, audio data).
     * Nothing was written to the original.
     */
    data class VerificationFailed(val reason: String) : TagWriteResult

    /** The file changed on the device while the new tags were prepared; nothing was written to it. */
    data object FileChanged : TagWriteResult

    /**
     * Writing to the original failed. When [restored] is true the original is back as it was;
     * otherwise its backup is kept and restoring is retried on the next start and before the next write.
     */
    data class ReplaceFailed(val restored: Boolean) : TagWriteResult
}

/**
 * Writes tags into audio files safely (ARCHITECTURE.md, section 5): the new tags go into a working copy in the
 * app's private storage, the copy is checked by reading it back, and only then replaces the original, with a
 * backup that restores the original after any failure. Implemented in `core:tags`.
 *
 * Writes run one at a time; calls do file I/O and switch to the I/O dispatcher themselves.
 */
interface TagWriter {
    /**
     * Changes the tags of [target] to [edit].
     *
     * @param id3v2VersionForNewTags ID3v2 version (3 or 4) for an MP3 file without an ID3v2 tag;
     *   a file with a tag keeps its version.
     */
    suspend fun writeTags(
        target: TagWriteTarget,
        edit: TagEdit,
        id3v2VersionForNewTags: Int = DEFAULT_ID3V2_VERSION,
    ): TagWriteResult

    companion object {
        /** ID3v2 version written to MP3 files without one until the setting from task 0.5.1 exists. */
        const val DEFAULT_ID3V2_VERSION = 4
    }
}
