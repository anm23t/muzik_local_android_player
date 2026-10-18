package org.muzzzlo.player.core.tags

import org.muzzzlo.player.core.model.AudioFormat
import org.muzzzlo.player.core.model.TagEdit

/**
 * Applies a [TagEdit] to a TagLib property map read from a file: the reverse of [PropertyMapper].
 *
 * The result is the full map to write back, so unknown fields survive (`setProperties` drops mapped fields
 * missing from the map, docs/decisions/005-tag-library.md, risk 7). Only fields whose value differs from what
 * [PropertyMapper] reads out of the map are rewritten; an unchanged field keeps its stored form.
 *
 * How changed fields are stored:
 * - several values (artists, album artists, genres) stay separate values, except in ID3v2.3, which has no such
 *   values and gets one string joined with `; `;
 * - ID3v2 and MP4 store a track or disc as `3/10` in `TRACKNUMBER` / `DISCNUMBER`; a total without its number
 *   cannot be stored there and is dropped. Vorbis comments store the number alone and the total in
 *   `TRACKTOTAL` / `DISCTOTAL`, or in `TOTALTRACKS` / `TOTALDISCS` when the file already spells it so;
 * - the year replaces the whole date (`DATE`);
 * - lyrics go to the key they were read from (`LYRICS`, `LYRICS:<description>` or `UNSYNCEDLYRICS`), `LYRICS`
 *   for a file without lyrics; removing them removes every lyrics key, so no other text shows up instead.
 */
internal object PropertyEditor {
    /** Separator of several values in one ID3v2.3 string; the same rule is used when reading (section 4). */
    const val ID3V23_SEPARATOR = "; "

    private const val ID3V23 = 3

    /**
     * The property map with [edit] applied.
     *
     * @param id3v2Version ID3v2 version (3 or 4) the file will be saved with; null for formats without ID3v2.
     */
    fun apply(
        properties: Map<String, List<String>>,
        edit: TagEdit,
        format: AudioFormat,
        id3v2Version: Int?,
    ): Map<String, List<String>> {
        val current = PropertyMapper.toTrackTags(properties, pictureCount = 0)
        val joinValues = format == AudioFormat.MP3 && id3v2Version == ID3V23
        val combinedPositions = format == AudioFormat.MP3 || format == AudioFormat.M4A
        val result = LinkedHashMap(properties)

        result.setSingle("TITLE", current.title, edit.title.cleaned())
        result.setValues("ARTIST", current.artists, edit.artists.cleaned(), joinValues)
        result.setSingle("ALBUM", current.album, edit.album.cleaned())
        result.setValues("ALBUMARTIST", current.albumArtists, edit.albumArtists.cleaned(), joinValues)
        result.setValues("GENRE", current.genres, edit.genres.cleaned(), joinValues)
        result.setPosition(
            Field.TRACK,
            PropertyMapper.Position(current.trackNumber, current.trackTotal),
            PropertyMapper.Position(edit.trackNumber.cleaned(), edit.trackTotal.cleaned()),
            combinedPositions,
        )
        result.setPosition(
            Field.DISC,
            PropertyMapper.Position(current.discNumber, current.discTotal),
            PropertyMapper.Position(edit.discNumber.cleaned(), edit.discTotal.cleaned()),
            combinedPositions,
        )
        result.setYear(current.year, edit.year.cleaned())
        result.setLyrics(current.lyrics, edit.lyrics?.takeIf { it.isNotBlank() })
        return result
    }

    /** Keys of the number and of the total of a track or a disc. */
    private enum class Field(val numberKey: String, val totalKeys: List<String>) {
        TRACK("TRACKNUMBER", listOf("TRACKTOTAL", "TOTALTRACKS")),
        DISC("DISCNUMBER", listOf("DISCTOTAL", "TOTALDISCS")),
    }

    private fun MutableMap<String, List<String>>.setSingle(
        key: String,
        current: String?,
        wanted: String?,
    ) {
        if (wanted == current) return
        put(key, wanted)
    }

    private fun MutableMap<String, List<String>>.setValues(
        key: String,
        current: List<String>,
        wanted: List<String>,
        joinValues: Boolean,
    ) {
        val stored = if (joinValues && wanted.size > 1) listOf(wanted.joinToString(ID3V23_SEPARATOR)) else wanted
        if (stored == current) return
        if (stored.isEmpty()) remove(key) else put(key, stored)
    }

    private fun MutableMap<String, List<String>>.setPosition(
        field: Field,
        current: PropertyMapper.Position,
        wanted: PropertyMapper.Position,
        combined: Boolean,
    ) {
        if (wanted == current) return
        if (combined) {
            val number = wanted.number
            put(field.numberKey, number?.let { if (wanted.total != null) "$it/${wanted.total}" else "$it" })
            field.totalKeys.forEach { remove(it) }
        } else {
            put(field.numberKey, wanted.number?.toString())
            val totalKey = field.totalKeys.firstOrNull { containsKey(it) } ?: field.totalKeys.first()
            field.totalKeys.forEach { remove(it) }
            put(totalKey, wanted.total?.toString())
        }
    }

    private fun MutableMap<String, List<String>>.setYear(
        current: Int?,
        wanted: Int?,
    ) {
        if (wanted == current) return
        remove("YEAR")
        put("DATE", wanted?.toString())
    }

    private fun MutableMap<String, List<String>>.setLyrics(
        current: String?,
        wanted: String?,
    ) {
        if (wanted == current) return
        val lyricsKeys = keys.filter { it == "LYRICS" || it.startsWith("LYRICS:") || it == "UNSYNCEDLYRICS" }
        if (wanted == null) {
            lyricsKeys.forEach { remove(it) }
            return
        }
        // The same order PropertyMapper reads them in, so the text read back is the one written.
        val source =
            lyricsKeys.sortedBy {
                if (it == "LYRICS") {
                    0
                } else if (it == "UNSYNCEDLYRICS") {
                    2
                } else {
                    1
                }
            }
                .firstOrNull { key -> get(key).orEmpty().any { it.isNotBlank() } }
        put(source ?: "LYRICS", wanted)
    }

    /** Puts a single value, or removes the key for null. */
    private fun MutableMap<String, List<String>>.put(
        key: String,
        value: String?,
    ) {
        if (value == null) remove(key) else put(key, listOf(value))
    }

    private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun List<String>.cleaned(): List<String> = map { it.trim() }.filter { it.isNotEmpty() }

    private fun Int?.cleaned(): Int? = this?.takeIf { it > 0 }
}
