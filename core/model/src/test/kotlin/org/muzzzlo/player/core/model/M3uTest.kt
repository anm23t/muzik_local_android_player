package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class M3uTest {
    @Test
    fun `extended playlist keeps duration and title of each entry`() {
        val text =
            """
            #EXTM3U
            #EXTINF:215,Artist - Song
            Music/Artist/01 Song.flac

            #EXTINF:-1,Stream
            /storage/emulated/0/Music/b.mp3
            plain.mp3
            """.trimIndent()

        assertEquals(
            listOf(
                M3uEntry("Music/Artist/01 Song.flac", 215, "Artist - Song"),
                M3uEntry("/storage/emulated/0/Music/b.mp3", null, "Stream"),
                M3uEntry("plain.mp3"),
            ),
            M3u.parse(text),
        )
    }

    @Test
    fun `windows line ends, attributes, comments and other directives`() {
        val text = "#EXTM3U\r\n#PLAYLIST:Mix\r\n# comment\r\n#EXTINF:12 tvg-id=\"x\",A, B - C\r\nC:\\Music\\c.mp3\r\n"

        assertEquals(listOf(M3uEntry("C:\\Music\\c.mp3", 12, "A, B - C")), M3u.parse(text))
    }

    @Test
    fun `EXTINF without comma or with a broken number`() =
        assertEquals(
            listOf(M3uEntry("a.mp3", 7, null), M3uEntry("b.mp3", null, "x")),
            M3u.parse("#EXTINF:7\na.mp3\n#EXTINF:abc,x\nb.mp3"),
        )

    @Test
    fun `EXTINF applies only to the next entry`() =
        assertEquals(listOf(M3uEntry("a.mp3", 5, "A"), M3uEntry("b.mp3")), M3u.parse("#EXTINF:5,A\na.mp3\nb.mp3"))

    @Test
    fun `empty file has no entries`() {
        assertEquals(emptyList<M3uEntry>(), M3u.parse(""))
        assertEquals(emptyList<M3uEntry>(), M3u.parse("#EXTM3U\n\n"))
    }

    @Test
    fun `utf-8 with and without byte order mark`() {
        val text = "#EXTM3U\nМузыка/ёлка.mp3\n"
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

        assertEquals(text, M3u.decode(text.toByteArray(Charsets.UTF_8)))
        assertEquals(text, M3u.decode(bom + text.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `bytes that are not utf-8 are read as windows-1251`() {
        val text = "Музыка\\Кино.mp3"

        assertEquals(text, M3u.decode(text.toByteArray(charset("windows-1251"))))
    }

    @Test
    fun `encode writes header, rounded seconds and one line per field`() {
        val items =
            listOf(
                M3uItem("/storage/emulated/0/Music/a.flac", 214_600, listOf("A", "B"), "Song"),
                M3uItem("/x/b.mp3", 0, emptyList(), "Line\nbreak"),
            )

        assertEquals(
            "#EXTM3U\n#EXTINF:215,A, B - Song\n/storage/emulated/0/Music/a.flac\n#EXTINF:-1,Line break\n/x/b.mp3\n",
            M3u.encode(items),
        )
    }

    @Test
    fun `encoded file reads back`() {
        val items = listOf(M3uItem("/m/Кино - Группа крови.mp3", 285_000, listOf("Кино"), "Группа крови"))

        assertEquals(
            listOf(M3uEntry("/m/Кино - Группа крови.mp3", 285, "Кино - Группа крови")),
            M3u.parse(M3u.decode(M3u.encode(items).toByteArray())),
        )
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        "'/storage/emulated/0/Music/A.mp3', 'storage/emulated/0/music/a.mp3'",
        "'C:\\Music\\A.mp3', 'music/a.mp3'",
        "'..\\..\\Music\\a.mp3', 'music/a.mp3'",
        "'./a.mp3', 'a.mp3'",
        "'file:///sdcard/My%20Music/%D1%91.mp3', 'sdcard/my music/ё.mp3'",
        "'file:///a/100%.mp3', 'a/100%.mp3'",
        "'Music//a.mp3', 'music/a.mp3'",
        "'a%20b.mp3', 'a%20b.mp3'",
    )
    fun `path parts`(
        path: String,
        expected: String,
    ) = assertEquals(expected.split('/'), M3uMatcher.parts(path))

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "'Road trip.m3u8', 'Road trip'",
        "'Mix.M3U', 'Mix'",
        "'  Late   night .m3u', 'Late night'",
        "'notes.txt', 'notes.txt'",
        "'.m3u8', 'Playlist'",
        "'', 'Playlist'",
    )
    fun `playlist name from file name`(
        fileName: String,
        expected: String,
    ) = assertEquals(expected, M3u.playlistName(fileName, "Playlist"))
}
