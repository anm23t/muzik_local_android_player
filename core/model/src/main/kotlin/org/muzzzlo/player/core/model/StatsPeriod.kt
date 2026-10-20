package org.muzzzlo.player.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Time range of a statistics query, in the time zone of the user. */
sealed interface StatsPeriod {
    /** The current calendar day. */
    data object Today : StatsPeriod

    /** The current calendar week, it starts on Monday. */
    data object Week : StatsPeriod

    /** The current calendar month. */
    data object Month : StatsPeriod

    /** The current calendar year. */
    data object Year : StatsPeriod

    /** Everything ever listened. */
    data object AllTime : StatsPeriod

    /** From [fromMs] (inclusive) to [toMs] (exclusive), wall clock milliseconds. */
    data class Custom(val fromMs: Long, val toMs: Long) : StatsPeriod
}

/** Half-open range `[fromMs, toMs)` of `listen_event.started_at`. */
data class StatsRange(val fromMs: Long, val toMs: Long) {
    companion object {
        val ALL = StatsRange(Long.MIN_VALUE, Long.MAX_VALUE)
    }
}

/** The range of [this] period when it is [nowMs] in [zone]. Calendar periods end at the start of the next one. */
fun StatsPeriod.rangeAt(
    nowMs: Long,
    zone: ZoneId,
): StatsRange {
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val (first, next) =
        when (this) {
            StatsPeriod.AllTime -> return StatsRange.ALL
            is StatsPeriod.Custom -> return StatsRange(fromMs, toMs)
            StatsPeriod.Today -> today to today.plusDays(1)
            StatsPeriod.Week ->
                today.with(
                    TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY),
                ).let { it to it.plusWeeks(1) }
            StatsPeriod.Month -> today.withDayOfMonth(1).let { it to it.plusMonths(1) }
            StatsPeriod.Year -> today.withDayOfYear(1).let { it to it.plusYears(1) }
        }
    return StatsRange(
        first.atStartOfDay(zone).toInstant().toEpochMilli(),
        next.atStartOfDay(zone).toInstant().toEpochMilli(),
    )
}
