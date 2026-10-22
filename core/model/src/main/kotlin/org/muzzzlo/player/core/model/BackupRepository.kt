package org.muzzzlo.player.core.model

/**
 * What an import changed; `unmatched` marks and playlist tracks could not be linked to a track of the library.
 *
 * @property playlists playlists added; one that is already there (same name and creation time) is skipped.
 */
data class BackupImportResult(
    val aliases: Int,
    val compounds: Int,
    val marks: Int,
    val events: Int,
    val unmatchedMarks: Int,
    val playlists: Int = 0,
    val unmatchedPlaylistTracks: Int = 0,
)

/** Exports and imports the data that lives only in the app (ARCHITECTURE.md, section 8). */
interface BackupRepository {
    /** The JSON backup text of the current data and settings. */
    suspend fun exportJson(): String

    /** The CSV text of all listen events. */
    suspend fun exportEventsCsv(): String

    /**
     * Merges the backup [text] into the data without duplicates and applies its settings.
     * Throws [BackupFormatException] before anything changes when the text is not a usable backup.
     */
    suspend fun importJson(text: String): BackupImportResult
}
