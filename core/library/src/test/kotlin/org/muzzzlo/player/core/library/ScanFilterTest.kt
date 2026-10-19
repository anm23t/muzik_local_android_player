package org.muzzzlo.player.core.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.muzzzlo.player.core.model.AppSettings

class ScanFilterTest {
    private fun file(
        durationMs: Long = 200_000,
        path: String? = "Music/",
    ) = ScannedFile(
        mediaStoreId = 1,
        uri = "content://1",
        relativePath = path,
        displayName = "a.mp3",
        title = "a",
        artist = null,
        album = null,
        year = null,
        trackNumber = null,
        discNumber = null,
        durationMs = durationMs,
        mimeType = null,
        fileSize = null,
        dateAdded = null,
        dateModified = null,
    )

    @ParameterizedTest
    @CsvSource(
        "29999, 30, false",
        "30000, 30, true",
        "30001, 30, true",
        "0, 0, true",
        "1, 0, true",
        "599999, 600, false",
    )
    fun `minimum length is inclusive`(
        durationMs: Long,
        minSeconds: Int,
        expected: Boolean,
    ) {
        val settings = AppSettings(minTrackSeconds = minSeconds)
        assertEquals(expected, ScanFilter.accepts(file(durationMs), settings))
    }

    @Test
    fun `an excluded folder is matched exactly, subfolders are separate`() {
        val settings = AppSettings(minTrackSeconds = 0, excludedFolders = setOf("Music/Podcasts/"))
        assertEquals(false, ScanFilter.accepts(file(path = "Music/Podcasts/"), settings))
        assertEquals(true, ScanFilter.accepts(file(path = "Music/Podcasts/Extra/"), settings))
        assertEquals(true, ScanFilter.accepts(file(path = "Music/"), settings))
    }

    @Test
    fun `a file without a path belongs to the root folder entry`() {
        val settings = AppSettings(minTrackSeconds = 0, excludedFolders = setOf(""))
        assertEquals(false, ScanFilter.accepts(file(path = null), settings))
        assertEquals(true, ScanFilter.accepts(file(path = "Music/"), settings))
    }

    @Test
    fun `apply keeps order and drops rejected files`() {
        val kept = file(path = "A/")
        val files = listOf(kept, file(path = "B/"), file(durationMs = 1000))
        val result = ScanFilter.apply(files, AppSettings(excludedFolders = setOf("B/")))
        assertEquals(listOf(kept), result)
    }
}
