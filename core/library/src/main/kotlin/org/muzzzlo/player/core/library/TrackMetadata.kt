package org.muzzzlo.player.core.library

import org.muzzzlo.player.core.model.ArtistSplitter
import org.muzzzlo.player.core.model.AudioFormat
import org.muzzzlo.player.core.model.CompoundNames
import org.muzzzlo.player.core.model.ReplayGainInfo
import org.muzzzlo.player.core.model.TagReadResult

/**
 * What the indexer writes for one file: tags from the file itself when they could be read,
 * MediaStore values otherwise.
 *
 * @property artistTagValues raw values of the artist tag as the file holds them (`track.artist_tag_values`).
 * @property artists the track artists: [artistTagValues] split by [ArtistSplitter] (ARCHITECTURE.md, section 4).
 * @property albumArtistValues artists the album is credited to: the album artist tag split the same way, or the
 * track artists when the file has none (DESIGN.md, section 3: the album artist comes from its own tag).
 * @property isDamaged the file crashed the native tag reader and is quarantined.
 * @property replayGain ReplayGain values from the tags; none when the tags could not be read.
 */
internal data class TrackMetadata(
    val title: String,
    val artistTagValues: List<String>,
    val artists: List<String>,
    val albumArtistValues: List<String>,
    val album: String?,
    val year: Int?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val trackTotal: Int?,
    val discTotal: Int?,
    val durationMs: Long,
    val bitrateKbps: Int?,
    val format: String?,
    val hasCover: Boolean,
    val hasLyrics: Boolean,
    val isDamaged: Boolean,
    val replayGain: ReplayGainInfo = ReplayGainInfo.NONE,
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
    compounds: CompoundNames = CompoundNames.None,
): TrackMetadata =
    when (result) {
        is TagReadResult.Success -> {
            val tags = result.fileTags.tags
            val audio = result.fileTags.audio
            TrackMetadata(
                title = tags.title ?: file.title,
                artistTagValues = tags.artists,
                artists = splitArtists(tags.artists, compounds),
                albumArtistValues =
                    albumArtists(
                        splitArtists(tags.albumArtists, compounds),
                        splitArtists(tags.artists, compounds),
                    ),
                album = tags.album,
                year = tags.year,
                trackNumber = tags.trackNumber,
                discNumber = tags.discNumber,
                trackTotal = tags.trackTotal,
                discTotal = tags.discTotal,
                durationMs = audio?.durationMs?.takeIf { it > 0 } ?: file.durationMs,
                bitrateKbps = audio?.bitrateKbps?.takeIf { it > 0 },
                format = result.fileTags.format.columnValue(),
                hasCover = tags.hasCover,
                hasLyrics = tags.lyrics != null,
                isDamaged = false,
                replayGain = result.fileTags.replayGain,
            )
        }
        TagReadResult.Unreadable -> mediaStoreMetadata(file, isDamaged = false, compounds)
        TagReadResult.Quarantined -> mediaStoreMetadata(file, isDamaged = true, compounds)
    }

private fun mediaStoreMetadata(
    file: ScannedFile,
    isDamaged: Boolean,
    compounds: CompoundNames,
) = TrackMetadata(
    title = file.title,
    artistTagValues = artistNames(file.artist),
    artists = splitArtists(artistNames(file.artist), compounds),
    albumArtistValues = splitArtists(artistNames(file.artist), compounds),
    album = cleanAlbum(file.album),
    year = file.year,
    trackNumber = file.trackNumber,
    discNumber = file.discNumber,
    // MediaStore knows the track number only, not the totals.
    trackTotal = null,
    discTotal = null,
    durationMs = file.durationMs,
    bitrateKbps = null,
    format = file.mimeType?.substringAfter('/')?.removePrefix("x-"),
    hasCover = false,
    hasLyrics = false,
    isDamaged = isDamaged,
)

/** Splits raw artist tag values into names; [compounds] are the names with a comma inside. */
private fun splitArtists(
    values: List<String>,
    compounds: CompoundNames,
): List<String> = ArtistSplitter.split(values, compounds)

/** The album artist tag without blank values; the track artists stand in when nothing is left. */
internal fun albumArtists(
    albumArtistTag: List<String>,
    trackArtists: List<String>,
): List<String> = albumArtistTag.map(String::trim).filter(String::isNotEmpty).ifEmpty { trackArtists }

/** Value of `track.format` (DATABASE.md). */
private fun AudioFormat.columnValue(): String =
    when (this) {
        AudioFormat.MP3 -> "mp3"
        AudioFormat.FLAC -> "flac"
        AudioFormat.M4A -> "m4a"
        AudioFormat.OGG_VORBIS -> "ogg"
        AudioFormat.OPUS -> "opus"
    }
