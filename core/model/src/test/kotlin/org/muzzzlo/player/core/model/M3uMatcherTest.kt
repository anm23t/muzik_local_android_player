package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class M3uMatcherTest {
    private val library =
        listOf(
            LibraryFile(1, "/storage/emulated/0/Music/Kino/01 Song.mp3", 200_000),
            LibraryFile(2, "/storage/emulated/0/Music/Other/01 Song.mp3", 180_000),
            LibraryFile(3, "/storage/emulated/0/Music/Single.flac", 100_000),
            LibraryFile(4, "/storage/1234-ABCD/Music/Kino/02 Next.mp3", 150_000),
        )

    private fun match(vararg entries: M3uEntry) = M3uMatcher.match(entries.toList(), library)

    private fun ids(vararg entries: M3uEntry) = match(*entries).trackIds

    @Test
    fun `same absolute path`() = assertEquals(listOf(1L), ids(M3uEntry("/storage/emulated/0/Music/Kino/01 Song.mp3")))

    @Test
    fun `path from another device by folder and name`() {
        assertEquals(listOf(2L), ids(M3uEntry("C:\\Users\\me\\Music\\Other\\01 Song.mp3")))
        assertEquals(listOf(1L), ids(M3uEntry("/sdcard/Music/Kino/01 Song.mp3")))
        assertEquals(listOf(4L), ids(M3uEntry("../Kino/02 Next.mp3")))
    }

    @Test
    fun `case does not matter`() = assertEquals(listOf(3L), ids(M3uEntry("music/SINGLE.FLAC")))

    @Test
    fun `name alone is the fallback`() = assertEquals(listOf(3L), ids(M3uEntry("D:\\Old\\Single.flac")))

    @Test
    fun `same name in two folders, duration decides`() =
        assertEquals(listOf(2L), ids(M3uEntry("Elsewhere/01 Song.mp3", durationSeconds = 180)))

    @Test
    fun `same name in two folders without duration takes the first file`() =
        assertEquals(listOf(1L), ids(M3uEntry("01 Song.mp3")))

    @Test
    fun `longer path tail beats duration`() =
        assertEquals(listOf(1L), ids(M3uEntry("x/Kino/01 Song.mp3", durationSeconds = 180)))

    @Test
    fun `missing files are reported in order and found ones keep the order of the file`() {
        val result =
            match(
                M3uEntry("Single.flac"),
                M3uEntry("C:\\gone.mp3"),
                M3uEntry("Kino/01 Song.mp3"),
                M3uEntry("Single.flac"),
                M3uEntry("other.ogg"),
            )

        assertEquals(listOf(3L, 1L, 3L), result.trackIds)
        assertEquals(listOf("C:\\gone.mp3", "other.ogg"), result.unmatched)
    }

    @Test
    fun `a folder with the same name as a file is not a match`() =
        assertEquals(emptyList<Long>(), ids(M3uEntry("Music/Kino")))

    @Test
    fun `empty playlist and empty library`() {
        assertEquals(M3uMatch(emptyList(), emptyList()), M3uMatcher.match(emptyList(), library))
        assertEquals(M3uMatch(emptyList(), listOf("a.mp3")), M3uMatcher.match(listOf(M3uEntry("a.mp3")), emptyList()))
    }
}
