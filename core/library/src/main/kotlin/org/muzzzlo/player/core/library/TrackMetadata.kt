package org.muzzzlo.player.core.library

import org.muzzzlo.player.core.model.AudioFormat
import org.muzzzlo.player.core.model.TagReadResult

/**
 * What the indexer writes for one file: tags from the file itself when they could be read,
 * MediaStore values otherwise.
 *
 * @property artistTagValues raw values of the artist tag; each one becomes an artist until splitting arrives (0.4.1).
 * @property isDamaged the file crashed the native tag reader and is quarantined.
 */
internal data class TrackMetadata(
    val title: String,
    val artistTagValues: List<String>,
    val album: String?,
    val year: Int?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val durationMs: Long,
    val bitrateKbps: Int?,
    val format: String?,
    val hasCover: Boolean,
    val hasLyrics: Boolean,
    val isDamaged: Boolean,
)

/**
 * Combines a MediaStore row with the result of reading the file's tags.
 *
 * Tags from the file win: MediaStore parses tags with its own rules, loses multiple artist values and knows nothing
 * about covers or lyrics. MediaStore still fills what the file lacks: the title (MediaStore falls back to the file
 * name) and the duration when TagLib could not compute one.
 */
internal fun trackMetadata(
    file: ScannedFile,
    result: TagReadResult,
): TrackMetadata =
    when (result) {
        is TagReadResult.Success -> {
            val tags = result.fileTags.tags
            val audio = result.fileTags.audio
            TrackMetadata(
                title = tags.title ?: file.title,
                artistTagValues = tags.artists,
                album = tags.album,
                year = tags.year,
                trackNumber = tags.trackNumber,
                discNumber = tags.discNumber,
                durationMs = audio?.durationMs?.takeIf { it > 0 } ?: file.durationMs,
                bitrateKbps = audio?.bitrateKbps?.takeIf { it > 0 },
                format = result.fileTags.format.columnValue(),
                hasCover = tags.hasCover,
                hasLyrics = tags.lyrics != null,
                isDamaged = false,
            )
        }
        TagReadResult.Unreadable -> mediaStoreMetadata(file, isDamaged = false)
        TagReadResult.Quarantined -> mediaStoreMetadata(file, isDamaged = true)
    }

private fun mediaStoreMetadata(
    file: ScannedFile,
    isDamaged: Boolean,
) = TrackMetadata(
    title = file.title,
    artistTagValues = artistNames(file.artist),
    album = cleanAlbum(file.album),
    year = file.year,
    trackNumber = file.trackNumber,
    discNumber = file.discNumber,
    durationMs = file.durationMs,
    bitrateKbps = null,
    format = file.mimeType?.substringAfter('/')?.removePrefix("x-"),
    hasCover = false,
    hasLyrics = false,
    isDamaged = isDamaged,
)

/** Value of `track.format` (DATABASE.md). */
private fun AudioFormat.columnValue(): String =
    when (this) {
        AudioFormat.MP3 -> "mp3"
        AudioFormat.FLAC -> "flac"
        AudioFormat.M4A -> "m4a"
        AudioFormat.OGG_VORBIS -> "ogg"
        AudioFormat.OPUS -> "opus"
    }
