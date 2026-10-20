package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ListenRulesTest {
    @Test
    fun `defaults are half the track or four minutes, recording on`() {
        val stats = AppSettings().stats
        assertEquals(50, stats.thresholdPercent)
        assertEquals(4, stats.thresholdMinutes)
        assertFalse(stats.privateMode)
    }

    @ParameterizedTest
    @CsvSource(
        "50, 50",
        "10, 10",
        "90, 90",
        "0, 10",
        "-20, 10",
        "100, 90",
        "44, 40",
        "45, 50",
        "55, 60",
    )
    fun `percent is clamped and put on its step`(
        percent: Int,
        expected: Int,
    ) {
        assertEquals(expected, StatsSettings.clampPercent(percent))
    }

    @ParameterizedTest
    @CsvSource("4, 4", "1, 1", "10, 10", "0, 1", "-3, 1", "11, 10")
    fun `minutes are clamped`(
        minutes: Int,
        expected: Int,
    ) {
        assertEquals(expected, StatsSettings.clampMinutes(minutes))
    }

    @ParameterizedTest
    @CsvSource(
        // percent, minutes, duration, threshold
        "50, 4, 180000, 90000",
        "50, 4, 480000, 240000",
        "50, 4, 600000, 240000",
        "50, 4, 1000, 500",
        "10, 1, 300000, 30000",
        "90, 10, 300000, 270000",
        "50, 4, 0, 240000",
        "50, 4, -1, 240000",
        // out of range settings are clamped first
        "200, 0, 600000, 60000",
    )
    fun `threshold is the share of the track or the minutes, whichever is shorter`(
        percent: Int,
        minutes: Int,
        durationMs: Long,
        expected: Long,
    ) {
        assertEquals(expected, ListenRules.thresholdMs(StatsSettings(percent, minutes), durationMs))
    }

    @ParameterizedTest
    @CsvSource(
        // played, end, counted, skipped
        "90000, COMPLETED, true, false",
        "89999, COMPLETED, false, false",
        "90000, MANUAL, true, false",
        "89999, MANUAL, false, true",
        "1000, MANUAL, false, true",
        "89999, INTERRUPTED, false, false",
        "200000, INTERRUPTED, true, false",
    )
    fun `counted by threshold, skipped only when ended by the user and not counted`(
        playedMs: Long,
        end: ListenEnd,
        counted: Boolean,
        skipped: Boolean,
    ) {
        val event = ListenRules.eventOf(FinishedListen(TRACK, 1234, playedMs, end), StatsSettings())!!
        assertEquals(counted, event.counted)
        assertEquals(skipped, event.skipped)
    }

    @Test
    fun `event keeps a snapshot of the track`() {
        val event = ListenRules.eventOf(FinishedListen(TRACK, 1234, 5000, ListenEnd.COMPLETED), StatsSettings())
        assertEquals(
            ListenEvent(
                trackId = 7,
                startedAtMs = 1234,
                playedMs = 5000,
                durationMs = 180_000,
                counted = false,
                skipped = false,
                titleSnapshot = "Song",
                artistsSnapshot = listOf("A", "B"),
            ),
            event,
        )
    }

    @Test
    fun `listens shorter than a second are not recorded`() {
        assertNull(ListenRules.eventOf(FinishedListen(TRACK, 0, 999, ListenEnd.MANUAL), StatsSettings()))
        assertTrue(ListenRules.eventOf(FinishedListen(TRACK, 0, 1000, ListenEnd.MANUAL), StatsSettings()) != null)
    }

    @Test
    fun `private mode records nothing`() {
        val listen = FinishedListen(TRACK, 0, 180_000, ListenEnd.COMPLETED)
        assertNull(ListenRules.eventOf(listen, StatsSettings(privateMode = true)))
    }

    private companion object {
        val TRACK =
            Track(
                id = 7,
                uri = "content://media/7",
                title = "Song",
                artists = listOf("A", "B"),
                albumId = 3,
                albumTitle = "Album",
                durationMs = 180_000,
                trackNumber = 1,
            )
    }
}
