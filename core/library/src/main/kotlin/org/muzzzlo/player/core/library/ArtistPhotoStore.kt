package org.muzzzlo.player.core.library

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Keeps the artist photos the user picks (CLAUDE.md, decision 10), in `artist_photos` of the app's private folder.
 * Photos are not part of the JSON backup (ARCHITECTURE.md, section 2). Call off the main thread.
 */
internal class ArtistPhotoStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val images = ReducedImageStore(context.contentResolver, File(context.filesDir, FOLDER), MAX_SIDE)

        /** Stores a reduced copy of the picture at [sourceUri] and returns the path of the copy. */
        fun save(
            artistId: Long,
            sourceUri: String,
        ): String = images.save(artistId.toString(), sourceUri)

        /** Deletes a stored photo. Paths outside the photo folder are ignored. */
        fun delete(path: String?) = images.delete(path)

        private companion object {
            const val FOLDER = "artist_photos"

            /** The biggest photo on screen is 100 dp; 512 px stays sharp on dense screens. */
            const val MAX_SIDE = 512
        }
    }
