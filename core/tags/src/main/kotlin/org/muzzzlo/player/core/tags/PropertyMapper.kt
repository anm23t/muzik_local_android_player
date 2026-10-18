package org.muzzzlo.player.core.tags

import org.muzzzlo.player.core.model.TrackTags

/**
 * Turns a TagLib property map into [TrackTags].
 *
 * TagLib already maps format-specific fields to common keys: ID3v2 `TIT2`, Vorbis `TITLE` and MP4 `©nam` all
 * become `TITLE`; ID3v2.3 `TYER` becomes `DATE`; `USLT` and `©lyr` become `LYRICS`. What is left differs
 * between formats and is handled here:
 * - ID3v2 and MP4 store the track as `3/10` in `TRACKNUMBER`; Vorbis comments use `TRACKNUMBER=3` with
 *   `TRACKTOTAL=10` (or `TOTALTRACKS`). The same goes for discs.
 * - ID3v2 lyrics with a description come as `LYRICS:<description>`; some taggers write `UNSYNCEDLYRICS` to Vorbis.
 *
 * Single-line values are trimmed, blank values dropped. Lyrics keep their line breaks.
 */
internal object PropertyMapper {
    private val leadingYear = Regex("""^\s*(\d{4})""")

    fun toTrackTags(
        properties: Map<String, List<String>>,
        pictureCount: Int,
    ): TrackTags {
        val track = parsePosition(properties.first("TRACKNUMBER"))
        val disc = parsePosition(properties.first("DISCNUMBER"))
        return TrackTags(
            title = properties.first("TITLE"),
            artists = properties.all("ARTIST"),
            album = properties.first("ALBUM"),
            albumArtists = properties.all("ALBUMARTIST"),
            trackNumber = track.number,
            trackTotal = track.total ?: parsePositive(properties.first("TRACKTOTAL", "TOTALTRACKS")),
            discNumber = disc.number,
            discTotal = disc.total ?: parsePositive(properties.first("DISCTOTAL", "TOTALDISCS")),
            year = parseYear(properties.first("DATE", "YEAR")),
            genres = properties.all("GENRE"),
            lyrics = lyricsOf(properties),
            hasCover = pictureCount > 0,
        )
    }

    /** A number with an optional total: `3`, `3/10`, `03 / 10`. */
    data class Position(val number: Int?, val total: Int?)

    fun parsePosition(value: String?): Position {
        if (value == null) return Position(null, null)
        val number = value.substringBefore('/')
        val total = value.substringAfter('/', missingDelimiterValue = "")
        return Position(parsePositive(number), parsePositive(total))
    }

    /** The year of a date such as `2024`, `2024-05-01` or `2024-05-01T10:00`; null for anything else. */
    fun parseYear(value: String?): Int? =
        value?.let { leadingYear.find(it) }?.groupValues?.get(1)?.toInt()?.takeIf { it > 0 }

    private fun parsePositive(value: String?): Int? = value?.trim()?.toIntOrNull()?.takeIf { it > 0 }

    private fun lyricsOf(properties: Map<String, List<String>>): String? {
        val keys =
            buildList {
                add("LYRICS")
                properties.keys.filterTo(this) { it.startsWith("LYRICS:") }
                add("UNSYNCEDLYRICS")
            }
        return keys.firstNotNullOfOrNull { key -> properties[key]?.firstOrNull { it.isNotBlank() } }
    }

    /** First non-blank value of the first key that has one, trimmed. */
    private fun Map<String, List<String>>.first(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> all(key).firstOrNull() }

    private fun Map<String, List<String>>.all(key: String): List<String> =
        this[key].orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
}
