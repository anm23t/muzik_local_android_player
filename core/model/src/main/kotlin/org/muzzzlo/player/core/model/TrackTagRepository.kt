package org.muzzzlo.player.core.model

/**
 * Tags of library tracks for the tag editor: read straight from the track's file and written into it safely
 * through [TagWriter]. Implemented in `core:library`.
 *
 * Calls do file I/O and switch to the I/O dispatcher themselves.
 */
interface TrackTagRepository {
    /** The tags of the track's file. [TagReadResult.Unreadable] when the track or its file is gone. */
    suspend fun readTags(trackId: Long): TagReadResult

    /** The cover of the track's file, null when it has none or the file cannot be read. */
    suspend fun readCover(trackId: Long): EmbeddedPicture?

    /**
     * Writes [edit] into the track's file. On [TagWriteResult.Success] the track is read again, so the library
     * shows the new tags without a full scan. [TagWriteResult.Unreadable] when the track or its file is gone.
     */
    suspend fun writeTags(
        trackId: Long,
        edit: TagEdit,
    ): TagWriteResult
}
