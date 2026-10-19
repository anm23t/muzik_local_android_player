package org.muzzzlo.player.core.library

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.EmbeddedPicture
import org.muzzzlo.player.core.model.SettingsRepository
import org.muzzzlo.player.core.model.TagEdit
import org.muzzzlo.player.core.model.TagReadResult
import org.muzzzlo.player.core.model.TagWriteResult
import org.muzzzlo.player.core.model.TagWriteTarget
import org.muzzzlo.player.core.model.TagWriter
import org.muzzzlo.player.core.model.TrackTagRepository
import javax.inject.Inject

/**
 * [TrackTagRepository] over the MediaStore files of the library. Tags always come from the file itself, never
 * from the database cache (decision 1 in CLAUDE.md); after a successful write the track is read into the cache again.
 */
internal class DefaultTrackTagRepository
    @Inject
    constructor(
        private val files: TrackFiles,
        private val writer: TagWriter,
        private val dispatchers: DispatcherProvider,
        private val settings: SettingsRepository,
    ) : TrackTagRepository {
        override suspend fun readTags(trackId: Long): TagReadResult =
            withContext(dispatchers.io) {
                files.find(trackId)?.let { files.readTags(it) } ?: TagReadResult.Unreadable
            }

        override suspend fun readCover(trackId: Long): EmbeddedPicture? =
            withContext(dispatchers.io) {
                files.find(trackId)?.let { files.readCover(it) }
            }

        override suspend fun writeTags(
            trackId: Long,
            edit: TagEdit,
        ): TagWriteResult =
            withContext(dispatchers.io) {
                val file = files.find(trackId)
                // Without its name the tag library cannot tell the format.
                val fileName = file?.displayName ?: return@withContext TagWriteResult.Unreadable
                val result =
                    writer.writeTags(
                        TagWriteTarget.ContentUri(file.uri, fileName),
                        edit,
                        settings.settings.first().id3v2VersionForNewTags,
                    )
                if (result == TagWriteResult.Success) files.reindex(files.find(trackId) ?: file)
                result
            }
    }
