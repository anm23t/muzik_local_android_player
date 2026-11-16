package org.muzzzlo.player.core.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.database.dao.MixListenRow
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class MixListenTest {
    @Test
    fun `a listen gets its local day, its artists and its source`() {
        // 2026-11-16 23:30 UTC is already 2026-11-17 in Moscow.
        val startedAt = LocalDate.of(2026, 11, 16).atTime(23, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
        val row =
            MixListenRow(trackId = 5, startedAt = startedAt, artistsSnapshot = "[\"A\",\"B\"]", sourceKind = "dailyMix")

        val moscow = mixListenOf(row, ZoneId.of("Europe/Moscow"))
        val utc = mixListenOf(row, ZoneOffset.UTC)

        assertEquals(LocalDate.of(2026, 11, 17).toEpochDay(), moscow.epochDay)
        assertEquals(LocalDate.of(2026, 11, 16).toEpochDay(), utc.epochDay)
        assertEquals(listOf("A", "B"), moscow.artists)
        assertTrue(moscow.fromGenerated)
    }

    @Test
    fun `a broken snapshot gives no artists and a plain listen is not generated`() {
        val listen = mixListenOf(MixListenRow(null, 0, "not json", null), ZoneOffset.UTC)
        assertEquals(emptyList<String>(), listen.artists)
        assertFalse(listen.fromGenerated)
    }
}
