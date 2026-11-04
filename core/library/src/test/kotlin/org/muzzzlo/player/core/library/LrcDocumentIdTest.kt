package org.muzzzlo.player.core.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LrcDocumentIdTest {
    @Test
    fun `a file inside the opened folder gets its document id`() {
        val cases =
            listOf(
                Triple("primary:Music", "Music/Album/", "primary:Music/Album/Song.lrc"),
                Triple("primary:Music", "Music/", "primary:Music/Song.lrc"),
                Triple("primary:", "Music/Album/", "primary:Music/Album/Song.lrc"),
                Triple("primary:", "", "primary:Song.lrc"),
                Triple("1A2B-3C4D:Songs", "Songs/X/", "1A2B-3C4D:Songs/X/Song.lrc"),
                Triple("primary:music", "Music/Album/", "primary:Music/Album/Song.lrc"),
                Triple("primary:Music/", "Music/Album/", "primary:Music/Album/Song.lrc"),
            )
        for ((tree, folder, expected) in cases) {
            assertEquals(expected, lrcDocumentId(tree, folder, "Song.lrc"), "$tree + $folder")
        }
    }

    @Test
    fun `a file outside the opened folder is not reachable`() {
        assertEquals(null, lrcDocumentId("primary:Music", "Download/", "Song.lrc"))
        assertEquals(null, lrcDocumentId("primary:Music", "Musical/", "Song.lrc"))
        assertEquals(null, lrcDocumentId("primary:Music/Rock", "Music/", "Song.lrc"))
        assertEquals(null, lrcDocumentId("broken", "Music/", "Song.lrc"))
    }
}
