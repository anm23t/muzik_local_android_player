package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SeasonalWindowTest {
    private fun date(text: String) = LocalDate.parse(text)

    private val autumn = CalendarPeriod.SeasonOf(Season.AUTUMN, 2026)
    private val winter = CalendarPeriod.SeasonOf(Season.WINTER, 2027)
    private val year = CalendarPeriod.Year(2026)

    @Test
    fun `a season shows from its last seven days for fourteen days`() {
        assertFalse(SeasonalWindow.isShown(autumn, date("2026-11-23")))
        assertTrue(SeasonalWindow.isShown(autumn, date("2026-11-24")))
        assertTrue(SeasonalWindow.isShown(autumn, date("2026-12-07")))
        assertFalse(SeasonalWindow.isShown(autumn, date("2026-12-08")))
    }

    @Test
    fun `a year shows from december 25`() {
        assertEquals(date("2026-12-25"), SeasonalWindow.firstShownDay(year))
        assertFalse(SeasonalWindow.isShown(year, date("2026-12-24")))
        assertTrue(SeasonalWindow.isShown(year, date("2027-01-07")))
        assertFalse(SeasonalWindow.isShown(year, date("2027-01-08")))
    }

    @Test
    fun `winter ends with a leap february`() {
        assertEquals(date("2028-02-23"), SeasonalWindow.firstShownDay(winter))
        assertTrue(SeasonalWindow.isShown(winter, date("2028-02-29")))
        assertTrue(SeasonalWindow.isShown(winter, date("2028-03-07")))
        assertFalse(SeasonalWindow.isShown(winter, date("2028-03-08")))
    }

    @Test
    fun `the home screen shows the season and the year of their windows`() {
        assertEquals(listOf(autumn), SeasonalWindow.shownOn(date("2026-12-03")))
        assertEquals(listOf(year), SeasonalWindow.shownOn(date("2026-12-26")))
        assertEquals(listOf(year), SeasonalWindow.shownOn(date("2027-01-02")))
        assertEquals(emptyList<CalendarPeriod>(), SeasonalWindow.shownOn(date("2026-10-15")))
    }

    @Test
    fun `a set is built every day until the end, once after it, then frozen`() {
        val lastDay = date("2026-11-30").toEpochDay()
        val afterEnd = lastDay + 3
        assertTrue(SeasonalWindow.needsBuild(autumn, builtDay = null, today = lastDay - 6))
        assertTrue(SeasonalWindow.needsBuild(autumn, builtDay = lastDay - 1, today = lastDay))
        assertFalse(SeasonalWindow.needsBuild(autumn, builtDay = lastDay, today = lastDay))
        assertTrue(SeasonalWindow.needsBuild(autumn, builtDay = lastDay, today = afterEnd))
        assertFalse(SeasonalWindow.needsBuild(autumn, builtDay = lastDay + 1, today = afterEnd))
        assertTrue(SeasonalWindow.needsBuild(autumn, builtDay = null, today = afterEnd))
    }
}
