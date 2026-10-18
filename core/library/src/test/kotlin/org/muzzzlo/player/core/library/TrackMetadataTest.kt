package org.muzzzlo.player.core.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.model.AudioFormat
import org.muzzzlo.player.core.model.AudioInfo
import org.muzzzlo.player.core.model.FileTags
import org.muzzzlo.player.core.model.TagReadResult
import org.muzzzlo.player.core.model.TrackTags

class TrackMetadataTest {
    private val mediaStore =
        ScannedFile(
            mediaStoreId = 7,
            uri = "content://media/external/audio/media/7",
            relativePath = "Music/",
            displayName = "file-name.flac",
            title = "file-name",
            artist = "Alpha; Beta",
            album = "MediaStore Album",
            year = 1999,
            trackNumber = 5,
            discNumber = 2,
            durationMs = 61_000,
            mimeType = "audio/x-flac",
            fileSize = 1234,
            dateAdded = 1,
            dateModified = 2,
        )

    @Test
    fun `tags from the file win over MediaStore`() {
        val tags =
            TrackTags(
                title = "Song",
                artists = listOf("Alpha", "Beta"),
                album = "Album",
                trackNumber = 3,
                discNumber = 1,
                year = 2024,
                lyrics = "[00:01.00]la",
                hasCover = true,
            )

        val metadata = trackMetadata(mediaStore, success(tags, AudioInfo(60_500, 900, 44_100, 2)))

        assertEquals(
            TrackMetadata(
                title = "Song",
                artistTagValues = listOf("Alpha", "Beta"),
                album = "Album",
                year = 2024,
                trackNumber = 3,
                discNumber = 1,
                durationMs = 60_500,
                bitrateKbps = 900,
                format = "flac",
                hasCover = true,
                hasLyrics = true,
                isDamaged = false,
            ),
            metadata,
        )
    }

    @Test
    fun `an untagged file keeps the MediaStore title and duration but no other MediaStore tags`() {
        val metadata = trackMetadata(mediaStore, success(TrackTags(), audio = null))

        assertEquals("file-name", metadata.title)
        assertEquals(61_000, metadata.durationMs)
        assertEquals(emptyList<String>(), metadata.artistTagValues)
        assertNull(metadata.album)
        assertNull(metadata.year)
        assertNull(metadata.bitrateKbps)
        assertFalse(metadata.hasCover)
        assertFalse(metadata.hasLyrics)
    }

    @Test
    fun `a zero duration from the file falls back to MediaStore`() {
        val metadata = trackMetadata(mediaStore, success(TrackTags(), AudioInfo(0, 0, 44_100, 2)))

        assertEquals(61_000, metadata.durationMs)
        assertNull(metadata.bitrateKbps)
    }

    @Test
    fun `an unreadable file is indexed from MediaStore`() {
        val metadata = trackMetadata(mediaStore, TagReadResult.Unreadable)

        assertEquals(mediaStoreMetadata(isDamaged = false), metadata)
    }

    @Test
    fun `a quarantined file is indexed from MediaStore and marked damaged`() {
        val metadata = trackMetadata(mediaStore, TagReadResult.Quarantined)

        assertTrue(metadata.isDamaged)
        assertEquals(mediaStoreMetadata(isDamaged = true), metadata)
    }

    @Test
    fun `format names follow the database column`() {
        val expected =
            mapOf(
                AudioFormat.MP3 to "mp3",
                AudioFormat.FLAC to "flac",
                AudioFormat.M4A to "m4a",
                AudioFormat.OGG_VORBIS to "ogg",
                AudioFormat.OPUS to "opus",
            )
        for ((format, column) in expected) {
            val result = TagReadResult.Success(FileTags(format, null, TrackTags(), null))
            assertEquals(column, trackMetadata(mediaStore, result).format)
        }
    }

    private fun success(
        tags: TrackTags,
        audio: AudioInfo?,
    ) = TagReadResult.Success(FileTags(AudioFormat.FLAC, id3v2Version = null, tags = tags, audio = audio))

    private fun mediaStoreMetadata(isDamaged: Boolean) =
        TrackMetadata(
            title = "file-name",
            artistTagValues = listOf("Alpha; Beta"),
            album = "MediaStore Album",
            year = 1999,
            trackNumber = 5,
            discNumber = 2,
            durationMs = 61_000,
            bitrateKbps = null,
            format = "flac",
            hasCover = false,
            hasLyrics = false,
            isDamaged = isDamaged,
        )
}
