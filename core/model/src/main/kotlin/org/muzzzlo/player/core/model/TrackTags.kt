package org.muzzzlo.player.core.model

/**
 * Tags of one audio file as they are written in it, before any processing.
 *
 * Artists stay raw: one value per value stored in the file (Vorbis comments, ID3v2.4 and MP4 hold several,
 * ID3v2.3 holds one string such as `A; B`). Splitting them into artists is the job of the artist pipeline
 * (ARCHITECTURE.md, section 4). Empty strings are dropped, so a missing field is null or an empty list.
 *
 * @property title track title.
 * @property artists values of the artist field in file order.
 * @property album album title.
 * @property albumArtists values of the album artist field in file order.
 * @property trackNumber number of the track on its disc.
 * @property trackTotal number of tracks on the disc.
 * @property discNumber number of the disc.
 * @property discTotal number of discs in the release.
 * @property year release year, taken from the date field (`2024`, `2024-05-01`).
 * @property genres values of the genre field.
 * @property lyrics lyrics text as stored, unsynchronised or LRC; line breaks are kept.
 * @property hasCover true when the file has at least one embedded picture.
 */
data class TrackTags(
    val title: String? = null,
    val artists: List<String> = emptyList(),
    val album: String? = null,
    val albumArtists: List<String> = emptyList(),
    val trackNumber: Int? = null,
    val trackTotal: Int? = null,
    val discNumber: Int? = null,
    val discTotal: Int? = null,
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    val lyrics: String? = null,
    val hasCover: Boolean = false,
)

/**
 * Audio stream properties of a file.
 *
 * @property durationMs length in milliseconds.
 * @property bitrateKbps average bitrate in kilobits per second.
 * @property sampleRateHz sample rate in hertz.
 * @property channels number of channels.
 */
data class AudioInfo(
    val durationMs: Long,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val channels: Int,
)

/** Container formats the tag library reads and writes (docs/decisions/005-tag-library.md). */
enum class AudioFormat {
    MP3,
    FLAC,
    M4A,
    OGG_VORBIS,
    OPUS,
}

/**
 * Everything read from a file in one pass.
 *
 * @property format container format.
 * @property id3v2Version major ID3v2 version (3 or 4) of an MP3 file with an ID3v2 tag, null otherwise.
 *   A write keeps this version (ARCHITECTURE.md, section 5).
 * @property tags the tags.
 * @property audio stream properties; null when they were not requested or could not be read.
 * @property replayGain ReplayGain values; read only, a tag write leaves them in the file as they are.
 */
data class FileTags(
    val format: AudioFormat,
    val id3v2Version: Int?,
    val tags: TrackTags,
    val audio: AudioInfo?,
    val replayGain: ReplayGainInfo = ReplayGainInfo.NONE,
)

/**
 * A picture embedded in a file, usually the cover.
 *
 * @property data encoded image bytes (JPEG, PNG and so on).
 * @property mimeType MIME type stored with the picture; may be empty when the file does not say.
 * @property pictureType ID3v2 / FLAC picture type, for example 3 = front cover, 0 = other.
 */
class EmbeddedPicture(
    val data: ByteArray,
    val mimeType: String,
    val pictureType: Int,
) {
    override fun equals(other: Any?): Boolean =
        other is EmbeddedPicture &&
            data.contentEquals(other.data) &&
            mimeType == other.mimeType &&
            pictureType == other.pictureType

    override fun hashCode(): Int = (data.contentHashCode() * 31 + mimeType.hashCode()) * 31 + pictureType

    override fun toString(): String = "EmbeddedPicture(${data.size} bytes, $mimeType, type $pictureType)"

    companion object {
        /** Picture type of the front cover in ID3v2 and FLAC. */
        const val TYPE_FRONT_COVER = 3
    }
}
