package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ListenCsvTest {
    private fun event(
        title: String,
        artists: List<String> = listOf("A", "B"),
    ) = BackupEvent(5, 6, 7, counted = true, skipped = false, title = title, artists = artists)

    @Test
    fun `header and plain row`() {
        val csv = ListenCsv.encode(listOf(event("Song")), formatTime = { "T$it" })
        assertEquals("started_at,played_ms,duration_ms,counted,skipped,title,artists\r\nT5,6,7,1,0,Song,A; B\r\n", csv)
    }

    @Test
    fun `no events gives the header only`() {
        assertEquals(1, ListenCsv.encode(emptyList()) { "" }.lines().filter { it.isNotEmpty() }.size)
    }

    @Test
    fun `commas quotes and line breaks are quoted`() {
        val csv = ListenCsv.encode(listOf(event("Tyler, \"The\"\nCreator", listOf("Tyler, The Creator")))) { "t" }
        assertEquals("\"Tyler, \"\"The\"\"\nCreator\",\"Tyler, The Creator\"\r\n", csv.substringAfter("t,6,7,1,0,"))
    }

    @Test
    fun `formula starts are neutralised`() {
        val csv = ListenCsv.encode(listOf(event("=1+1", emptyList()))) { "t" }
        assertEquals("'=1+1,\r\n", csv.substringAfter("t,6,7,1,0,"))
    }
}
