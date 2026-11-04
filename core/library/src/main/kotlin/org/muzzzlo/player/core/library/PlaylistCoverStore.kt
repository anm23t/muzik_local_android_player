package org.muzzzlo.player.core.library

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Keeps the covers the user picks for playlists (task 2.0.4), in `playlist_covers` of the app's private folder.
 * Like the artist photos they are not part of the JSON backup. Call off the main thread.
 */
internal class PlaylistCoverStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val images = ReducedImageStore(context.contentResolver, File(context.filesDir, FOLDER), MAX_SIDE)

        /** Stores a reduced copy of the picture at [sourceUri] and returns the path of the copy. */
        fun save(
            playlistId: Long,
            sourceUri: String,
        ): String = images.save(playlistId.toString(), sourceUri)

        /** Deletes a stored cover. Paths outside the cover folder are ignored. */
        fun delete(path: String?) = images.delete(path)

        private companion object {
            const val FOLDER = "playlist_covers"

            /** The biggest playlist cover on screen is the album card cover; 512 px stays sharp on dense screens. */
            const val MAX_SIDE = 512
        }
    }
