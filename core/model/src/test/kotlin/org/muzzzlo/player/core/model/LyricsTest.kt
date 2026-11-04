package org.muzzzlo.player.core.model

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LyricsTest {
    private fun synced(text: String) = LrcParser.parse(text) as Lyrics.Synced

    private fun lines(vararg pairs: Pair<Long, String>) = pairs.map { LyricLine(it.first, it.second) }

    @Test
    fun `time tags of every precision`() {
        val cases =
            listOf(
                "[00:12]A" to 12_000L,
                "[00:12.5]A" to 12_500L,
                "[00:12.05]A" to 12_050L,
                "[00:12.005]A" to 12_005L,
                "[01:02.30]A" to 62_300L,
                "[00:12:30]A" to 12_300L,
                "[105:00.00]A" to 6_300_000L,
            )
        for ((text, time) in cases) assertEquals(lines(time to "A"), synced(text).lines, text)
    }

    @Test
    fun `several tags on a line give a line each, in time order`() {
        val lyrics = synced("[00:30.00]Verse\n[00:10.00][00:50.00]Chorus")
        assertEquals(lines(10_000L to "Chorus", 30_000L to "Verse", 50_000L to "Chorus"), lyrics.lines)
    }

    @Test
    fun `service tags are left out`() {
        val text = "[ar:Artist]\n[ti:Title]\n[al:Album]\n[by:Someone]\n[length:03:20]\n[re:Editor]\n[00:01.00]Line"
        assertEquals(lines(1_000L to "Line"), synced(text).lines)
    }

    @Test
    fun `a positive offset shows the lines earlier, a negative one later`() {
        assertEquals(lines(500L to "A", 9_500L to "B"), synced("[offset:+500]\n[00:01.00]A\n[00:10.00]B").lines)
        assertEquals(lines(1_250L to "A"), synced("[offset:-250]\n[00:01.00]A").lines)
        assertEquals(lines(0L to "A"), synced("[OFFSET: 3000]\n[00:01.00]A").lines)
    }

    @Test
    fun `an empty timed line is a pause, an empty untimed line is skipped`() {
        assertEquals(lines(1_000L to "A", 5_000L to "", 9_000L to "B"), synced("[00:01]A\n\n[00:05]\n[00:09]B").lines)
    }

    @Test
    fun `broken tags and untimed text in synced lyrics are left out`() {
        val text = "[00:01.00]Good\n[0a:12]Broken\n[00:61]Bad seconds\nNo tag\n[00:02.00]Also good"
        assertEquals(lines(1_000L to "Good", 2_000L to "Also good"), synced(text).lines)
    }

    @Test
    fun `word times of the extended format are dropped`() {
        assertEquals(lines(1_000L to "Hello world"), synced("[00:01.00]<00:01.00>Hello <00:01.50>world").lines)
    }

    @Test
    fun `text without time tags is plain, brackets and all`() {
        assertEquals(Lyrics.Plain("[Chorus]\nLa la"), LrcParser.parse("﻿[Chorus]\r\nLa la\r\n"))
        assertEquals(Lyrics.Plain("Just text"), LrcParser.parse("  Just text  "))
    }

    @Test
    fun `blank text has no lyrics`() {
        assertNull(LrcParser.parse(""))
        assertNull(LrcParser.parse(" \n\t\n"))
        assertNull(LrcParser.parse("﻿"))
    }

    @Test
    fun `the current line is the last one started`() {
        val lyrics = Lyrics.Synced(lines(1_000L to "A", 5_000L to "B", 5_000L to "B2", 9_000L to "C"))
        val cases =
            listOf(
                0L to -1,
                999L to -1,
                1_000L to 0,
                4_999L to 0,
                5_000L to 2,
                8_000L to 2,
                9_000L to 3,
                100_000L to 3,
            )
        for ((position, index) in cases) assertEquals(index, lyrics.lineAt(position), "at $position")
    }

    @Test
    fun `the tag wins and the file is not read then`() =
        runBlocking {
            var fileRead = false
            val chosen =
                chooseLyrics("Tag text") {
                    fileRead = true
                    "[00:01]File"
                }
            assertEquals(TrackLyrics(Lyrics.Plain("Tag text"), LyricsSource.TAG), chosen)
            assertFalse(fileRead)
        }

    @Test
    fun `an empty tag falls back to the file, nothing gives none`() =
        runBlocking {
            val fromFile = chooseLyrics("  ") { "[00:01]File" }
            assertEquals(LyricsSource.FILE, fromFile?.source)
            assertEquals(lines(1_000L to "File"), (fromFile?.lyrics as Lyrics.Synced).lines)
            assertNull(chooseLyrics(null) { null })
            assertNull(chooseLyrics(null) { "" })
        }

    @Test
    fun `auto scroll waits after the user scrolled`() {
        assertTrue(LyricsScroll.autoScrollAllowed(userScrolling = false, lastUserScrollEndMs = null, nowMs = 10))
        assertFalse(LyricsScroll.autoScrollAllowed(userScrolling = true, lastUserScrollEndMs = null, nowMs = 10))
        assertFalse(LyricsScroll.autoScrollAllowed(userScrolling = false, lastUserScrollEndMs = 1_000, nowMs = 4_999))
        assertTrue(LyricsScroll.autoScrollAllowed(userScrolling = false, lastUserScrollEndMs = 1_000, nowMs = 5_000))
    }
}
