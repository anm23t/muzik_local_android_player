package org.muzzzlo.player.core.tags

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.muzzzlo.player.core.model.AudioFormat
import org.muzzzlo.player.core.model.TagEdit
import java.util.stream.Stream

class PropertyEditorTest {
    @Test
    fun `an edit equal to the file changes nothing`() {
        val properties =
            mapOf(
                "TITLE" to listOf("Song"),
                "DATE" to listOf("2024-05-01"),
                "TRACKNUMBER" to listOf("03"),
                "TOTALTRACKS" to listOf("10"),
                "LYRICS:lrc" to listOf("text"),
                "REPLAYGAIN_TRACK_GAIN" to listOf("-6.5 dB"),
            )
        val edit = TagEdit.from(PropertyMapper.toTrackTags(properties, pictureCount = 0))

        assertEquals(properties, PropertyEditor.apply(properties, edit, AudioFormat.OGG_VORBIS, null))
    }

    @Test
    fun `unknown fields are kept when known ones change`() {
        val properties = mapOf("TITLE" to listOf("Old"), "MY_FIELD" to listOf("a", "b"), "PRIV" to listOf("x"))

        val result = PropertyEditor.apply(properties, TagEdit(title = "New"), AudioFormat.FLAC, null)

        assertEquals(mapOf("TITLE" to listOf("New"), "MY_FIELD" to listOf("a", "b"), "PRIV" to listOf("x")), result)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `writes a changed field`(
        @Suppress("UNUSED_PARAMETER") name: String,
        case: Case,
    ) {
        val result = PropertyEditor.apply(case.before, case.edit, case.format, case.id3v2Version)

        assertEquals(case.after, result)
    }

    /** One row of the edge-case table: the map before, the edit, the map after. */
    data class Case(
        val format: AudioFormat,
        val id3v2Version: Int?,
        val before: Map<String, List<String>>,
        val edit: TagEdit,
        val after: Map<String, List<String>>,
    )

    companion object {
        private fun row(
            name: String,
            format: AudioFormat,
            id3v2Version: Int?,
            before: Map<String, List<String>>,
            edit: TagEdit,
            after: Map<String, List<String>>,
        ): Arguments = Arguments.of(name, Case(format, id3v2Version, before, edit, after))

        private val mp3 = AudioFormat.MP3
        private val flac = AudioFormat.FLAC

        @JvmStatic
        @Suppress("LongMethod") // one row per edge case
        fun cases(): Stream<Arguments> =
            Stream.of(
                row(
                    "artists stay separate in ID3v2.4",
                    mp3,
                    4,
                    emptyMap(),
                    TagEdit(artists = listOf("A", "B")),
                    mapOf("ARTIST" to listOf("A", "B")),
                ),
                row(
                    "artists are joined with ; in ID3v2.3",
                    mp3,
                    3,
                    emptyMap(),
                    TagEdit(artists = listOf("Tyler, The Creator", "B")),
                    mapOf("ARTIST" to listOf("Tyler, The Creator; B")),
                ),
                row(
                    "split artists equal to the joined ID3v2.3 string are not rewritten",
                    mp3,
                    3,
                    mapOf("ARTIST" to listOf("A; B")),
                    TagEdit(artists = listOf("A", "B")),
                    mapOf("ARTIST" to listOf("A; B")),
                ),
                row(
                    "genres and album artists are joined in ID3v2.3",
                    mp3,
                    3,
                    emptyMap(),
                    TagEdit(albumArtists = listOf("A", "B"), genres = listOf("Rock", "Pop")),
                    mapOf("ALBUMARTIST" to listOf("A; B"), "GENRE" to listOf("Rock; Pop")),
                ),
                row(
                    "Vorbis comments keep several values",
                    flac,
                    null,
                    emptyMap(),
                    TagEdit(artists = listOf("A", "B")),
                    mapOf("ARTIST" to listOf("A", "B")),
                ),
                row(
                    "values are trimmed and blank ones dropped",
                    flac,
                    null,
                    emptyMap(),
                    TagEdit(title = "  Song ", artists = listOf(" A ", " ", "")),
                    mapOf("TITLE" to listOf("Song"), "ARTIST" to listOf("A")),
                ),
                row(
                    "an empty field removes it",
                    flac,
                    null,
                    mapOf("TITLE" to listOf("Song"), "ARTIST" to listOf("A")),
                    TagEdit(title = " "),
                    emptyMap(),
                ),
                row(
                    "a title with an emoji outside the BMP",
                    flac,
                    null,
                    emptyMap(),
                    TagEdit(title = "Трек 🎧"),
                    mapOf("TITLE" to listOf("Трек 🎧")),
                ),
                row(
                    "ID3v2 stores track and total in one value",
                    mp3,
                    4,
                    mapOf("TRACKNUMBER" to listOf("1")),
                    TagEdit(trackNumber = 3, trackTotal = 10),
                    mapOf("TRACKNUMBER" to listOf("3/10")),
                ),
                row(
                    "MP4 stores the disc and its total in one value",
                    AudioFormat.M4A,
                    null,
                    emptyMap(),
                    TagEdit(discNumber = 1, discTotal = 2),
                    mapOf("DISCNUMBER" to listOf("1/2")),
                ),
                row(
                    "ID3v2 drops a total without its number",
                    mp3,
                    4,
                    mapOf("TRACKNUMBER" to listOf("3/10")),
                    TagEdit(trackTotal = 12),
                    emptyMap(),
                ),
                row(
                    "Vorbis comments store the total in TRACKTOTAL",
                    flac,
                    null,
                    emptyMap(),
                    TagEdit(trackNumber = 3, trackTotal = 10),
                    mapOf("TRACKNUMBER" to listOf("3"), "TRACKTOTAL" to listOf("10")),
                ),
                row(
                    "Vorbis comments keep the TOTALDISCS spelling",
                    AudioFormat.OGG_VORBIS,
                    null,
                    mapOf("DISCNUMBER" to listOf("1"), "TOTALDISCS" to listOf("2")),
                    TagEdit(discNumber = 2, discTotal = 3),
                    mapOf("DISCNUMBER" to listOf("2"), "TOTALDISCS" to listOf("3")),
                ),
                row(
                    "a combined Vorbis value is split into number and total",
                    flac,
                    null,
                    mapOf("TRACKNUMBER" to listOf("3/10")),
                    TagEdit(trackNumber = 4, trackTotal = 10),
                    mapOf("TRACKNUMBER" to listOf("4"), "TRACKTOTAL" to listOf("10")),
                ),
                row(
                    "zero and negative numbers mean no number",
                    flac,
                    null,
                    mapOf("TRACKNUMBER" to listOf("3")),
                    TagEdit(trackNumber = 0, discNumber = -1),
                    emptyMap(),
                ),
                row(
                    "a new year replaces the whole date",
                    flac,
                    null,
                    mapOf("DATE" to listOf("2024-05-01")),
                    TagEdit(year = 2023),
                    mapOf("DATE" to listOf("2023")),
                ),
                row(
                    "removing the year removes DATE and YEAR",
                    flac,
                    null,
                    mapOf("YEAR" to listOf("2024")),
                    TagEdit(),
                    emptyMap(),
                ),
                row(
                    "lyrics go to the description key they were read from",
                    mp3,
                    3,
                    mapOf("LYRICS:lrc" to listOf("old"), "LYRICS:other" to listOf("x")),
                    TagEdit(lyrics = "new"),
                    mapOf("LYRICS:lrc" to listOf("new"), "LYRICS:other" to listOf("x")),
                ),
                row(
                    "lyrics go to UNSYNCEDLYRICS when the file uses it",
                    AudioFormat.OPUS,
                    null,
                    mapOf("UNSYNCEDLYRICS" to listOf("old")),
                    TagEdit(lyrics = "new"),
                    mapOf("UNSYNCEDLYRICS" to listOf("new")),
                ),
                row(
                    "new lyrics keep line breaks and go to LYRICS",
                    flac,
                    null,
                    emptyMap(),
                    TagEdit(lyrics = "[00:01.00]one\n[00:02.00]two"),
                    mapOf("LYRICS" to listOf("[00:01.00]one\n[00:02.00]two")),
                ),
                row(
                    "removing lyrics removes every lyrics key",
                    mp3,
                    4,
                    mapOf("LYRICS" to listOf("a"), "LYRICS:lrc" to listOf("b"), "UNSYNCEDLYRICS" to listOf("c")),
                    TagEdit(lyrics = "  "),
                    emptyMap(),
                ),
            )
    }
}
