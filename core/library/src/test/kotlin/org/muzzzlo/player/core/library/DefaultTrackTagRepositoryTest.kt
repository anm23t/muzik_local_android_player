package org.muzzzlo.player.core.library

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.muzzzlo.player.core.model.AppLanguage
import org.muzzzlo.player.core.model.AppSettings
import org.muzzzlo.player.core.model.AudioFormat
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.EmbeddedPicture
import org.muzzzlo.player.core.model.FileTags
import org.muzzzlo.player.core.model.SettingsRepository
import org.muzzzlo.player.core.model.TagEdit
import org.muzzzlo.player.core.model.TagReadResult
import org.muzzzlo.player.core.model.TagWriteResult
import org.muzzzlo.player.core.model.TagWriteTarget
import org.muzzzlo.player.core.model.TagWriter
import org.muzzzlo.player.core.model.TrackTags

class DefaultTrackTagRepositoryTest {
    private val file = scannedFile(dateModified = 1)
    private val files = FakeTrackFiles(mutableMapOf(TRACK_ID to file))
    private val writer = FakeTagWriter(TagWriteResult.Success)
    private val settings = FakeSettings()
    private val repository = DefaultTrackTagRepository(files, writer, TestDispatchers, settings)

    @Test
    fun `tags are read from the track's file`() =
        runBlocking {
            assertEquals(TagReadResult.Success(fileTags), repository.readTags(TRACK_ID))
            assertEquals(listOf(file), files.read)
        }

    @Test
    fun `an unknown track reads as unreadable and has no cover`() =
        runBlocking {
            assertEquals(TagReadResult.Unreadable, repository.readTags(UNKNOWN_ID))
            assertNull(repository.readCover(UNKNOWN_ID))
        }

    @Test
    fun `the edit goes to the MediaStore file under its display name`() =
        runBlocking {
            val edit = TagEdit(title = "New")
            repository.writeTags(TRACK_ID, edit)
            assertEquals(listOf(TagWriteTarget.ContentUri(file.uri, "song.mp3") to edit), writer.writes)
        }

    @Test
    fun `the ID3 version of the settings goes to the writer`() =
        runBlocking {
            DefaultTrackTagRepository(
                files,
                writer,
                TestDispatchers,
                FakeSettings(AppSettings(id3v2VersionForNewTags = 3)),
            )
                .writeTags(TRACK_ID, TagEdit())
            repository.writeTags(TRACK_ID, TagEdit())
            assertEquals(listOf(3, 4), writer.id3Versions)
        }

    @Test
    fun `after a successful write the file is read into the library again with its new stamps`() =
        runBlocking {
            val rewritten = scannedFile(dateModified = 2)
            writer.onWrite = { files.byId[TRACK_ID] = rewritten }

            assertEquals(TagWriteResult.Success, repository.writeTags(TRACK_ID, TagEdit()))
            assertEquals(listOf(rewritten), files.reindexed)
        }

    @ParameterizedTest
    @MethodSource("failures")
    fun `a failed write leaves the library as it is`(failure: TagWriteResult) =
        runBlocking {
            writer.result = failure
            assertEquals(failure, repository.writeTags(TRACK_ID, TagEdit()))
            assertEquals(emptyList<ScannedFile>(), files.reindexed)
        }

    @Test
    fun `a track that is gone or has no file name is not written`() =
        runBlocking {
            files.byId[NAMELESS_ID] = file.copy(displayName = null)

            assertEquals(TagWriteResult.Unreadable, repository.writeTags(UNKNOWN_ID, TagEdit()))
            assertEquals(TagWriteResult.Unreadable, repository.writeTags(NAMELESS_ID, TagEdit()))
            assertEquals(emptyList<Any>(), writer.writes)
        }

    private class FakeSettings(initial: AppSettings = AppSettings()) : SettingsRepository {
        override val settings = flowOf(initial)

        override suspend fun setFolderIncluded(
            relativePath: String,
            included: Boolean,
        ) = Unit

        override suspend fun setMinTrackSeconds(seconds: Int) = Unit

        override suspend fun setId3v2VersionForNewTags(version: Int) = Unit

        override suspend fun setConfirmBeforeWrite(enabled: Boolean) = Unit

        override suspend fun setLanguage(language: AppLanguage) = Unit

        override suspend fun setDynamicAccent(enabled: Boolean) = Unit
    }

    private class FakeTrackFiles(val byId: MutableMap<Long, ScannedFile>) : TrackFiles {
        val read = mutableListOf<ScannedFile>()
        val reindexed = mutableListOf<ScannedFile>()

        override suspend fun find(trackId: Long) = byId[trackId]

        override suspend fun readTags(file: ScannedFile): TagReadResult {
            read += file
            return TagReadResult.Success(fileTags)
        }

        override suspend fun readCover(file: ScannedFile): EmbeddedPicture? = null

        override suspend fun reindex(file: ScannedFile) {
            reindexed += file
        }
    }

    private class FakeTagWriter(var result: TagWriteResult) : TagWriter {
        val writes = mutableListOf<Pair<TagWriteTarget, TagEdit>>()
        var onWrite: () -> Unit = {}
        var id3Versions = mutableListOf<Int>()

        override suspend fun writeTags(
            target: TagWriteTarget,
            edit: TagEdit,
            id3v2VersionForNewTags: Int,
        ): TagWriteResult {
            writes += target to edit
            id3Versions += id3v2VersionForNewTags
            onWrite()
            return result
        }
    }

    private object TestDispatchers : DispatcherProvider {
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
    }

    companion object {
        private const val TRACK_ID = 1L
        private const val NAMELESS_ID = 2L
        private const val UNKNOWN_ID = 99L

        private val fileTags = FileTags(AudioFormat.MP3, 4, TrackTags(title = "Old"), audio = null)

        private fun scannedFile(dateModified: Long) =
            ScannedFile(
                mediaStoreId = 5,
                uri = "content://media/external/audio/media/5",
                relativePath = "Music/",
                displayName = "song.mp3",
                title = "song",
                artist = null,
                album = null,
                year = null,
                trackNumber = null,
                discNumber = null,
                durationMs = 1_000,
                mimeType = "audio/mpeg",
                fileSize = 100,
                dateAdded = 1,
                dateModified = dateModified,
            )

        @JvmStatic
        fun failures(): List<TagWriteResult> =
            listOf(
                TagWriteResult.NeedsPermission,
                TagWriteResult.NotEnoughSpace(10),
                TagWriteResult.Unreadable,
                TagWriteResult.Quarantined,
                TagWriteResult.WriteFailed,
                TagWriteResult.VerificationFailed("audio"),
                TagWriteResult.FileChanged,
                TagWriteResult.ReplaceFailed(restored = true),
                TagWriteResult.ReplaceFailed(restored = false),
            )
    }
}
