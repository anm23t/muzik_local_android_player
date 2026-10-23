package org.muzzzlo.player.core.model

/**
 * What an import of a playlist file did.
 *
 * @property playlistId the new playlist; null when no entry matched a file of the library and nothing was created.
 * @property name the name of the new playlist, taken from the file name.
 * @property added entries that became tracks of the playlist.
 * @property unmatched paths of the entries without a file in the library, as written in the playlist.
 */
data class PlaylistImport(
    val playlistId: Long?,
    val name: String,
    val added: Int,
    val unmatched: List<String>,
)

/**
 * Import and export of playlists as M3U and M3U8 files ([M3u]). The files are chosen by the user with the system
 * picker and come as `content://` URIs in text, so no storage permission is needed. Implemented in `core:library`.
 */
interface PlaylistTransfer {
    /**
     * Reads the playlist file at [uri] and creates a playlist of the library tracks it lists ([M3uMatcher]),
     * named after the file or [fallbackName]. Throws [java.io.IOException] when the file cannot be read.
     */
    suspend fun import(
        uri: String,
        fallbackName: String,
    ): PlaylistImport

    /** Writes the visible tracks of the playlist to [uri] as M3U8; throws [java.io.IOException] on failure. */
    suspend fun export(
        playlistId: Long,
        uri: String,
    )
}
