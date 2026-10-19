package org.muzzzlo.player.feature.tageditor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.muzzzlo.player.core.model.CoverEdit
import org.muzzzlo.player.core.model.EmbeddedPicture
import org.muzzzlo.player.core.model.TagEdit
import org.muzzzlo.player.core.model.TrackTags

class TagDraftTest {
    private val tags =
        TrackTags(
            title = "Song",
            artists = listOf("Tyler, The Creator", "Kanye West"),
            album = "Album",
            albumArtists = listOf("Tyler, The Creator"),
            trackNumber = 3,
            trackTotal = 10,
            discNumber = 1,
            year = 2011,
            genres = listOf("Hip hop"),
            lyrics = "line one\nline two",
            hasCover = true,
        )

    @Test
    fun `a draft of the file tags writes them back unchanged`() {
        val draft = TagDraft.from(tags)

        assertEquals(TagEdit.from(tags), draft.toEdit())
        assertEquals(emptyList<TagChange>(), changesOf(tags, draft.toEdit()!!))
    }

    /** Number fields: the text typed, then the parsed value or the error. */
    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(
        "'', ''",
        "'  ', ''",
        "7, 7",
        "' 7 ', 7",
        "007, 7",
        "1, 1",
        "9999, 9999",
        "0, NotANumber",
        "10000, NotANumber",
        "-1, NotANumber",
        "+1, NotANumber",
        "3/10, NotANumber",
        "1.5, NotANumber",
        "abc, NotANumber",
        "99999999999, NotANumber",
        "'٣', NotANumber",
    )
    fun `the year is a whole number from 1 to 9999`(
        typed: String,
        expected: String,
    ) {
        val draft = TagDraft(year = typed)
        if (expected == FieldError.NotANumber.name) {
            assertEquals(mapOf(TagField.Year to FieldError.NotANumber), draft.errors())
            assertNull(draft.toEdit())
        } else {
            assertEquals(emptyMap<TagField, FieldError>(), draft.errors())
            assertEquals(expected.toIntOrNull(), draft.toEdit()?.year)
        }
    }

    @ParameterizedTest(name = "track \"{0}\" of \"{1}\", disc \"{2}\" of \"{3}\"")
    @MethodSource("pairs")
    fun `track and disc numbers are checked as pairs`(
        trackNumber: String,
        trackTotal: String,
        discNumber: String,
        discTotal: String,
        expected: Map<TagField, FieldError>,
    ) {
        val draft =
            TagDraft(trackNumber = trackNumber, trackTotal = trackTotal, discNumber = discNumber, discTotal = discTotal)
        assertEquals(expected, draft.errors())
    }

    @Test
    fun `texts are trimmed and blank list rows dropped`() {
        val draft =
            TagDraft(title = "  New  ", artists = listOf(" A ", "", "  ", "B"), album = "   ", lyrics = "\n text \n")

        val edit = draft.toEdit()!!

        assertEquals("New", edit.title)
        assertEquals(listOf("A", "B"), edit.artists)
        assertNull(edit.album)
        assertEquals("text", edit.lyrics)
    }

    @Test
    fun `changes show the values before and after in screen order`() {
        val draft =
            TagDraft.from(tags).copy(
                year = "2012",
                title = "New",
                artists = listOf("Tyler, The Creator"),
                trackNumber = "",
                trackTotal = "",
                genres = emptyList(),
            )

        assertEquals(
            listOf(
                TagChange(TagField.Title, "Song", "New"),
                TagChange(TagField.Artists, "Tyler, The Creator · Kanye West", "Tyler, The Creator"),
                TagChange(TagField.Track, "3/10", ""),
                TagChange(TagField.Year, "2011", "2012"),
                TagChange(TagField.Genres, "Hip hop", ""),
            ),
            changesOf(tags, draft.toEdit()!!),
        )
    }

    @Test
    fun `splitting one value into two is a change even when they read the same`() {
        val oneValue = tags.copy(artists = listOf("Alpha; Beta"))
        val draft = TagDraft.from(oneValue).copy(artists = listOf("Alpha", "Beta"))

        assertEquals(
            listOf(TagChange(TagField.Artists, "Alpha; Beta", "Alpha · Beta")),
            changesOf(oneValue, draft.toEdit()!!),
        )
    }

    @Test
    fun `reordering the artists is a change`() {
        val draft = TagDraft.from(tags).copy(artists = tags.artists.moveUp(1))

        assertEquals(
            listOf(TagChange(TagField.Artists, "Tyler, The Creator · Kanye West", "Kanye West · Tyler, The Creator")),
            changesOf(tags, draft.toEdit()!!),
        )
    }

    @Test
    fun `an added empty row or extra spaces are no change`() {
        val draft = TagDraft.from(tags).copy(title = " Song ", artists = tags.artists + "", genres = tags.genres + " ")
        assertEquals(emptyList<TagChange>(), changesOf(tags, draft.toEdit()!!))
    }

    @Test
    fun `lyrics and cover changes are listed without their contents`() {
        val picture = EmbeddedPicture(byteArrayOf(1), "image/png", EmbeddedPicture.TYPE_FRONT_COVER)
        val draft = TagDraft.from(tags).copy(lyrics = "other", cover = CoverEdit.Replace(picture))

        assertEquals(
            listOf(TagChange(TagField.Cover, "", ""), TagChange(TagField.Lyrics, "", "")),
            changesOf(tags, draft.toEdit()!!),
        )
    }

    @Test
    fun `removing the cover is a change only when the file has one`() {
        val draft = TagDraft.from(tags).copy(cover = CoverEdit.Remove)

        assertEquals(listOf(TagChange(TagField.Cover, "", "")), changesOf(tags, draft.toEdit()!!))
        assertEquals(emptyList<TagChange>(), changesOf(tags.copy(hasCover = false), draft.toEdit()!!))
    }

    companion object {
        @JvmStatic
        fun pairs(): List<Arguments> =
            listOf(
                Arguments.of("", "", "", "", emptyMap<TagField, FieldError>()),
                Arguments.of("3", "", "", "", emptyMap<TagField, FieldError>()),
                Arguments.of("3", "10", "1", "2", emptyMap<TagField, FieldError>()),
                // A number above its total is odd but valid: some releases number tracks across discs.
                Arguments.of("12", "10", "", "", emptyMap<TagField, FieldError>()),
                Arguments.of("", "10", "", "", mapOf(TagField.Track to FieldError.TotalWithoutNumber)),
                Arguments.of("", "", "", "2", mapOf(TagField.Disc to FieldError.TotalWithoutNumber)),
                Arguments.of("x", "10", "", "", mapOf(TagField.Track to FieldError.NotANumber)),
                Arguments.of("", "x", "", "", mapOf(TagField.Track to FieldError.NotANumber)),
                Arguments.of(
                    "0",
                    "",
                    "",
                    "x",
                    mapOf(TagField.Track to FieldError.NotANumber, TagField.Disc to FieldError.NotANumber),
                ),
            )
    }
}
