package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.stream.Stream

class StatsPeriodTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun range(
        @Suppress("UNUSED_PARAMETER") name: String,
        period: StatsPeriod,
        now: LocalDateTime,
        zone: ZoneId,
        from: LocalDateTime,
        to: LocalDateTime,
    ) {
        val expected = StatsRange(ms(from, zone), ms(to, zone))
        assertEquals(expected, period.rangeAt(ms(now, zone), zone))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixedCases")
    fun fixedRanges(
        @Suppress("UNUSED_PARAMETER") name: String,
        period: StatsPeriod,
        expected: StatsRange,
    ) {
        assertEquals(expected, period.rangeAt(1_000_000L, UTC))
    }

    companion object {
        private val UTC = ZoneId.of("UTC")
        private val NEW_YORK = ZoneId.of("America/New_York")

        private fun ms(
            time: LocalDateTime,
            zone: ZoneId,
        ) = time.atZone(zone).toInstant().toEpochMilli()

        private fun at(
            y: Int,
            m: Int,
            d: Int,
            h: Int = 0,
        ) = LocalDateTime.of(y, m, d, h, 0)

        @JvmStatic
        fun cases(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    "today midday",
                    StatsPeriod.Today,
                    at(2026, 3, 10, 12),
                    UTC,
                    at(2026, 3, 10),
                    at(2026, 3, 11),
                ),
                Arguments.of(
                    "today at midnight",
                    StatsPeriod.Today,
                    at(2026, 3, 10),
                    UTC,
                    at(2026, 3, 10),
                    at(2026, 3, 11),
                ),
                Arguments.of(
                    "today last hour of month",
                    StatsPeriod.Today,
                    at(2026, 1, 31, 23),
                    UTC,
                    at(2026, 1, 31),
                    at(2026, 2, 1),
                ),
                Arguments.of(
                    "week on Wednesday",
                    StatsPeriod.Week,
                    at(2026, 3, 11, 9),
                    UTC,
                    at(2026, 3, 9),
                    at(2026, 3, 16),
                ),
                Arguments.of(
                    "week on Monday",
                    StatsPeriod.Week,
                    at(2026, 3, 9, 9),
                    UTC,
                    at(2026, 3, 9),
                    at(2026, 3, 16),
                ),
                Arguments.of(
                    "week on Sunday",
                    StatsPeriod.Week,
                    at(2026, 3, 15, 23),
                    UTC,
                    at(2026, 3, 9),
                    at(2026, 3, 16),
                ),
                Arguments.of(
                    "week across a year",
                    StatsPeriod.Week,
                    at(2026, 1, 1, 9),
                    UTC,
                    at(2025, 12, 29),
                    at(2026, 1, 5),
                ),
                Arguments.of(
                    "month in the middle",
                    StatsPeriod.Month,
                    at(2026, 2, 14, 9),
                    UTC,
                    at(2026, 2, 1),
                    at(2026, 3, 1),
                ),
                Arguments.of(
                    "month in December",
                    StatsPeriod.Month,
                    at(2026, 12, 31, 23),
                    UTC,
                    at(2026, 12, 1),
                    at(2027, 1, 1),
                ),
                Arguments.of("year", StatsPeriod.Year, at(2024, 6, 1), UTC, at(2024, 1, 1), at(2025, 1, 1)),
                Arguments.of(
                    "today in a zone",
                    StatsPeriod.Today,
                    at(2026, 6, 1, 1),
                    NEW_YORK,
                    at(2026, 6, 1),
                    at(2026, 6, 2),
                ),
                Arguments.of(
                    "day with spring forward",
                    StatsPeriod.Today,
                    at(2026, 3, 8, 12),
                    NEW_YORK,
                    at(2026, 3, 8),
                    at(2026, 3, 9),
                ),
                Arguments.of(
                    "week with fall back",
                    StatsPeriod.Week,
                    at(2026, 11, 2, 12),
                    NEW_YORK,
                    at(2026, 11, 2),
                    at(2026, 11, 9),
                ),
            )

        @JvmStatic
        fun fixedCases(): Stream<Arguments> =
            Stream.of(
                Arguments.of("all time", StatsPeriod.AllTime, StatsRange.ALL),
                Arguments.of("custom is kept", StatsPeriod.Custom(5, 10), StatsRange(5, 10)),
            )
    }
}
