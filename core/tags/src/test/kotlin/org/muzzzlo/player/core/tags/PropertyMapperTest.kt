package org.muzzzlo.player.core.tags

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class PropertyMapperTest {
    @ParameterizedTest(name = "\"{0}\" -> {1} of {2}")
    @CsvSource(
        nullValues = ["null"],
        value = [
            "3, 3, null",
            "03, 3, null",
            "3/10, 3, 10",
            "' 3 / 10 ', 3, 10",
            "/10, null, 10",
            "3/, 3, null",
            "0, null, null",
            "0/0, null, null",
            "-1, null, null",
            "A, null, null",
            "3/x, 3, null",
            "'', null, null",
            "99999999999, null, null",
        ],
    )
    fun `parses track and disc positions`(
        value: String?,
        number: Int?,
        total: Int?,
    ) {
        assertEquals(PropertyMapper.Position(number, total), PropertyMapper.parsePosition(value ?: ""))
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(
        nullValues = ["null"],
        value = [
            "2024, 2024",
            "2024-05-01, 2024",
            "2024-05-01T10:00:00, 2024",
            "' 1999', 1999",
            "0000, null",
            "24, null",
            "May 2024, null",
            "'', null",
        ],
    )
    fun `takes the year from a date`(
        value: String?,
        year: Int?,
    ) {
        assertEquals(year, PropertyMapper.parseYear(value ?: ""))
    }

    @Test
    fun `missing position and date are null`() {
        assertEquals(PropertyMapper.Position(null, null), PropertyMapper.parsePosition(null))
        assertNull(PropertyMapper.parseYear(null))
    }

    @Test
    fun `keeps every artist value in file order without splitting`() {
        val tags = map("ARTIST" to listOf("Tyler, The Creator", "Kanye West")).toTags()

        assertEquals(listOf("Tyler, The Creator", "Kanye West"), tags.artists)
    }

    @Test
    fun `keeps an ID3v2_3 artist string whole`() {
        assertEquals(listOf("Alpha; Beta"), map("ARTIST" to listOf("Alpha; Beta")).toTags().artists)
    }

    @Test
    fun `trims values and drops blank ones`() {
        val tags =
            map(
                "TITLE" to listOf("  ", " Song "),
                "ARTIST" to listOf(" A ", "", "  ", "B"),
                "GENRE" to listOf(""),
            ).toTags()

        assertEquals("Song", tags.title)
        assertEquals(listOf("A", "B"), tags.artists)
        assertEquals(emptyList<String>(), tags.genres)
    }

    @Test
    fun `reads Vorbis totals from separate fields`() {
        val withTrackTotal = map("TRACKNUMBER" to listOf("3"), "TRACKTOTAL" to listOf("10")).toTags()
        val withTotalTracks = map("DISCNUMBER" to listOf("1"), "TOTALDISCS" to listOf("2")).toTags()

        assertEquals(3, withTrackTotal.trackNumber)
        assertEquals(10, withTrackTotal.trackTotal)
        assertEquals(1, withTotalTracks.discNumber)
        assertEquals(2, withTotalTracks.discTotal)
    }

    @Test
    fun `a total inside the number wins over a separate total field`() {
        val tags = map("TRACKNUMBER" to listOf("3/12"), "TRACKTOTAL" to listOf("10")).toTags()

        assertEquals(12, tags.trackTotal)
    }

    @Test
    fun `falls back to YEAR when there is no DATE`() {
        assertEquals(1987, map("YEAR" to listOf("1987")).toTags().year)
        assertEquals(2001, map("DATE" to listOf("2001"), "YEAR" to listOf("1987")).toTags().year)
    }

    @Test
    fun `lyrics keep line breaks and surrounding text`() {
        val lrc = "[00:01.00]One\n[00:02.00]Two\n"

        assertEquals(lrc, map("LYRICS" to listOf(lrc)).toTags().lyrics)
    }

    @Test
    fun `lyrics come from LYRICS, then a described ID3 frame, then UNSYNCEDLYRICS`() {
        assertEquals("plain", map("LYRICS" to listOf("plain"), "LYRICS:lrc" to listOf("described")).toTags().lyrics)
        val described = map("UNSYNCEDLYRICS" to listOf("u"), "LYRICS:lrc" to listOf("described"))
        assertEquals("described", described.toTags().lyrics)
        assertEquals("u", map("UNSYNCEDLYRICS" to listOf("u")).toTags().lyrics)
        assertEquals("u", map("LYRICS" to listOf("  "), "UNSYNCEDLYRICS" to listOf("u")).toTags().lyrics)
    }

    @Test
    fun `an empty map gives empty tags`() {
        val tags = map().toTags()

        assertNull(tags.title)
        assertEquals(emptyList<String>(), tags.artists)
        assertNull(tags.album)
        assertNull(tags.trackNumber)
        assertNull(tags.year)
        assertNull(tags.lyrics)
        assertFalse(tags.hasCover)
    }

    @Test
    fun `any picture means the file has a cover`() {
        assertTrue(PropertyMapper.toTrackTags(emptyMap(), pictureCount = 2).hasCover)
    }

    private fun map(vararg entries: Pair<String, List<String>>) = mapOf(*entries)

    private fun Map<String, List<String>>.toTags() = PropertyMapper.toTrackTags(this, pictureCount = 0)
}
