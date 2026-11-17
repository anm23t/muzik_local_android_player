package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class CalendarPeriodTest {
    private fun date(text: String) = LocalDate.parse(text)

    @ParameterizedTest(name = "{0} is in {1} {2}")
    @CsvSource(
        "2026-11-30, AUTUMN, 2026",
        "2026-12-01, WINTER, 2026",
        "2026-12-31, WINTER, 2026",
        "2027-01-01, WINTER, 2026",
        "2027-02-28, WINTER, 2026",
        "2028-02-29, WINTER, 2027",
        "2028-03-01, SPRING, 2028",
        "2026-05-31, SPRING, 2026",
        "2026-06-01, SUMMER, 2026",
        "2026-08-31, SUMMER, 2026",
        "2026-09-01, AUTUMN, 2026",
    )
    fun `seasons are calendar ones of the northern hemisphere`(
        day: String,
        season: Season,
        year: Int,
    ) {
        assertEquals(CalendarPeriod.SeasonOf(season, year), CalendarPeriod.seasonOf(date(day)))
    }

    @Test
    fun `winter crosses the new year and ends with a leap february`() {
        val winter = CalendarPeriod.SeasonOf(Season.WINTER, 2027)
        assertEquals(date("2027-12-01"), winter.start)
        assertEquals(date("2028-03-01"), winter.end)
        assertTrue(date("2028-02-29") in winter)
        assertFalse(date("2028-03-01") in winter)
    }

    @Test
    fun `seasons step back and forth over the new year`() {
        val winter = CalendarPeriod.SeasonOf(Season.WINTER, 2026)
        assertEquals(CalendarPeriod.SeasonOf(Season.AUTUMN, 2026), winter.previous())
        assertEquals(CalendarPeriod.SeasonOf(Season.SPRING, 2027), winter.next())
        assertEquals(
            CalendarPeriod.SeasonOf(Season.WINTER, 2025),
            CalendarPeriod.SeasonOf(Season.SPRING, 2026).previous(),
        )
    }

    @Test
    fun `a week starts on monday and may cross the new year`() {
        val week = CalendarPeriod.weekOf(date("2027-01-01"))
        assertEquals(date("2026-12-28"), week.start)
        assertEquals(date("2027-01-04"), week.end)
        assertEquals(53, week.number)
        assertEquals(1, week.next().number)
        assertEquals(47, CalendarPeriod.weekOf(date("2026-11-17")).number)
    }

    @Test
    fun `a month and a year are calendar ones`() {
        val february = CalendarPeriod.monthOf(date("2028-02-10"))
        assertEquals(date("2028-03-01"), february.end)
        assertEquals(
            CalendarPeriod.Month(YearMonth.of(2027, 12)),
            CalendarPeriod.monthOf(date("2028-01-05")).previous(),
        )
        assertEquals(date("2027-01-01"), CalendarPeriod.yearOf(date("2026-06-01")).end)
    }

    @Test
    fun `a running period is not finished until its last day is over`() {
        val week = CalendarPeriod.weekOf(date("2026-11-17"))
        assertFalse(week.isFinishedOn(date("2026-11-22")))
        assertTrue(week.isFinishedOn(date("2026-11-23")))
    }

    @Test
    fun `every period comes back from its key`() {
        val periods =
            listOf(
                CalendarPeriod.weekOf(date("2026-11-17")),
                CalendarPeriod.monthOf(date("2026-11-17")),
                CalendarPeriod.SeasonOf(Season.WINTER, 2026),
                CalendarPeriod.Year(2026),
            )
        periods.forEach { assertEquals(it, CalendarPeriod.fromKey(it.key)) }
        listOf(null, "", "week:2026-11-17", "season:2026-monsoon", "year:x", "day:1").forEach {
            assertNull(CalendarPeriod.fromKey(it), "$it")
        }
    }

    @Test
    fun `the range runs from midnight to midnight of the zone`() {
        val zone = ZoneId.of("Europe/Moscow")
        val range = CalendarPeriod.Year(2026).rangeIn(zone)
        assertEquals(date("2026-01-01").atStartOfDay(zone).toInstant().toEpochMilli(), range.fromMs)
        assertEquals(date("2027-01-01").atStartOfDay(zone).toInstant().toEpochMilli(), range.toMs)
    }
}
