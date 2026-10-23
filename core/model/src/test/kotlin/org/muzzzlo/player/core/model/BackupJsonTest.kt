package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class BackupJsonTest {
    private val sample =
        BackupData(
            createdAtMs = 1_700_000_000_000,
            settings =
                AppSettings(
                    excludedFolders = setOf("Music/Podcasts/", "Music/\"Q\"/"),
                    minTrackSeconds = 45,
                    language = AppLanguage.RUSSIAN,
                    replayGain = ReplayGainSettings(mode = ReplayGainMode.TRACK, preampDb = 2.5f),
                    crossfade = CrossfadeSettings(seconds = 5, withinAlbum = true),
                    playbackBehavior = PlaybackBehaviorSettings(transientFocus = TransientFocusAction.PAUSE),
                    stats = StatsSettings(thresholdPercent = 30, thresholdMinutes = 2, privateMode = true),
                    theme = InterfaceTheme.HUGE,
                ),
            aliases = listOf(BackupAlias("tyler", "Tyler", "Tyler, The Creator")),
            compounds =
                listOf(
                    BackupCompound("Tyler, The Creator", "tyler|the creator", null, true),
                    BackupCompound("X", "x", "b1", false),
                ),
            marks =
                listOf(
                    BackupTrackMark(
                        "Song \u0442\u0435\u0441\u0442 \uD83C\uDFB5",
                        listOf("A", "B"),
                        123_456,
                        67,
                        true,
                        1_700_000_000_000,
                    ),
                    BackupTrackMark("Plain", emptyList(), 1, null, false),
                ),
            events =
                listOf(
                    BackupEvent(
                        10,
                        20,
                        30,
                        counted = true,
                        skipped = false,
                        title = "Line\nbreak \"q\"",
                        artists = listOf("A"),
                    ),
                ),
            playlists =
                listOf(
                    BackupPlaylist(
                        "Mix \uD83D\uDE97",
                        5,
                        9,
                        List(2) { BackupPlaylistTrack("Song", listOf("A"), 1000) },
                    ),
                    BackupPlaylist("Empty", 6, null, emptyList()),
                ),
        )

    @Test
    fun `round trip keeps everything`() {
        assertEquals(sample, BackupJson.decode(BackupJson.encode(sample)))
    }

    @Test
    fun `a version 1 file reads with no playlists`() {
        val v1 = """{"format":"muzzzlo-backup","schemaVersion":1,"marks":[]}"""

        assertEquals(emptyList<BackupPlaylist>(), BackupJson.decode(v1).playlists)
    }

    @Test
    fun `empty backup round trips`() {
        val empty = BackupData(0, AppSettings(), emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(empty, BackupJson.decode(BackupJson.encode(empty)))
    }

    @Test
    fun `missing settings read as defaults and bad values are clamped`() {
        val text =
            """{"format":"muzzzlo-backup","schemaVersion":1,""" +
                """"settings":{"crossfadeSeconds":99,"language":"KLINGON","id3v2VersionForNewTags":5,"theme":"NEON"}}"""
        val settings = BackupJson.decode(text).settings
        assertEquals(CrossfadeSettings.MAX_SECONDS, settings.crossfade.seconds)
        assertEquals(AppLanguage.SYSTEM, settings.language)
        assertEquals(AppSettings().id3v2VersionForNewTags, settings.id3v2VersionForNewTags)
        assertEquals(InterfaceTheme.WINDOW, settings.theme)
    }

    @Test
    fun `rating is clamped and unknown fields are ignored`() {
        val text =
            """{"format":"muzzzlo-backup","schemaVersion":1,"extra":1,""" +
                """"marks":[{"title":"t","durationMs":1,"rating":500,"x":null}]}"""
        assertEquals(100, BackupJson.decode(text).marks.single().ratingTenths)
    }

    @Test
    fun `a mark of an older backup has no favorite moment`() {
        val text =
            """{"format":"muzzzlo-backup","schemaVersion":2,"marks":[""" +
                """{"title":"t","durationMs":1,"favorite":true},""" +
                """{"title":"u","durationMs":1,"favorite":true,"favoritedAt":1700000000000}]}"""
        val marks = BackupJson.decode(text).marks
        assertEquals(listOf(null, 1_700_000_000_000), marks.map { it.favoritedAtMs })
    }

    @Test
    fun `newer version is refused`() {
        assertThrows(BackupFormatException::class.java) {
            BackupJson.decode("""{"format":"muzzzlo-backup","schemaVersion":${BackupJson.VERSION + 1}}""")
        }
    }

    @Test
    fun `not a backup is refused`() {
        listOf(
            "",
            "not json",
            "[]",
            "{}",
            """{"format":"other","schemaVersion":1}""",
            """{"format":"muzzzlo-backup"}""",
            """{"format":"muzzzlo-backup","schemaVersion":0}""",
            """{"format":"muzzzlo-backup","schemaVersion":1,"events":[{"title":"t"}]}""",
            """{"format":"muzzzlo-backup","schemaVersion":1,"events":[5]}""",
            """{"format":"muzzzlo-backup","schemaVersion":1,"aliases":[{"key":"k"}]}""",
            """{"format":"muzzzlo-backup","schemaVersion":1""",
        ).forEach { text ->
            assertThrows(BackupFormatException::class.java, { BackupJson.decode(text) }, "text: $text")
        }
    }

    @Test
    fun `json parser handles escapes numbers and nesting`() {
        val value = Json.parse(""" {"a":[1,-2,3.5,1e2,true,false,null,"\u0041\n\/"],"b":{}} """) as Map<*, *>
        assertEquals(listOf(1L, -2L, 3.5, 100.0, true, false, null, "A\n/"), value["a"])
        assertEquals(emptyMap<String, Any?>(), value["b"])
        assertNull(Json.parse("null"))
    }

    @Test
    fun `json parser rejects broken text`() {
        listOf("{", "[1,]", """{"a":}""", "\"abc", "tru", "1 2", """{"a" 1}""", "\"\\x\"", "\"\\u12\"", "01x").forEach {
            assertThrows(JsonException::class.java, { Json.parse(it) }, "text: $it")
        }
    }

    @Test
    fun `json writer escapes control characters`() {
        assertEquals("\"a\\u0001\\\"\\\\\"", Json.write("a\u0001\"\\"))
    }
}
