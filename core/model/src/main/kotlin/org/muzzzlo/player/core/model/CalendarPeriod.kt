package org.muzzzlo.player.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.time.Month as MonthOfYear

/** Calendar seasons of the northern hemisphere, in the order of the year that starts with winter. */
enum class Season(val key: String, val firstMonth: MonthOfYear) {
    /** December to February; it belongs to the year of its December. */
    WINTER("winter", MonthOfYear.DECEMBER),
    SPRING("spring", MonthOfYear.MARCH),
    SUMMER("summer", MonthOfYear.JUNE),
    AUTUMN("autumn", MonthOfYear.SEPTEMBER),
}

/**
 * A calendar period of seasonal playlists and period summaries (tasks 2.5.9, 2.5.11): a week from Monday, a month,
 * a season or a year. Every period is the half-open range of days `[start, end)`.
 *
 * @property key stable text form, stored in `generated_playlist.period` and used as a navigation argument.
 */
sealed interface CalendarPeriod {
    val start: LocalDate
    val end: LocalDate
    val key: String

    fun previous(): CalendarPeriod

    fun next(): CalendarPeriod

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && date.isBefore(end)

    /** The period is over on [today]; a running one is shown "so far". */
    fun isFinishedOn(today: LocalDate): Boolean = !today.isBefore(end)

    /** The week that starts on Monday [start]. */
    data class Week(override val start: LocalDate) : CalendarPeriod {
        init {
            require(start.dayOfWeek == DayOfWeek.MONDAY) { "A week starts on Monday: $start" }
        }

        override val end: LocalDate get() = start.plusWeeks(1)
        override val key: String get() = "week:$start"

        /** The ISO number of the week (1-53), as in "Week 47". */
        val number: Int get() = start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

        override fun previous() = Week(start.minusWeeks(1))

        override fun next() = Week(end)
    }

    data class Month(val month: YearMonth) : CalendarPeriod {
        override val start: LocalDate get() = month.atDay(1)
        override val end: LocalDate get() = month.plusMonths(1).atDay(1)
        override val key: String get() = "month:$month"

        override fun previous() = Month(month.minusMonths(1))

        override fun next() = Month(month.plusMonths(1))
    }

    /** A season; winter of [year] runs from December of [year] to February of the next year. */
    data class SeasonOf(val season: Season, val year: Int) : CalendarPeriod {
        override val start: LocalDate get() = LocalDate.of(year, season.firstMonth, 1)
        override val end: LocalDate get() = start.plusMonths(MONTHS_IN_SEASON)
        override val key: String get() = "season:$year-${season.key}"

        override fun previous(): SeasonOf =
            when (season) {
                Season.WINTER -> SeasonOf(Season.AUTUMN, year)
                Season.SPRING -> SeasonOf(Season.WINTER, year - 1)
                Season.SUMMER -> SeasonOf(Season.SPRING, year)
                Season.AUTUMN -> SeasonOf(Season.SUMMER, year)
            }

        override fun next(): SeasonOf =
            when (season) {
                Season.WINTER -> SeasonOf(Season.SPRING, year + 1)
                Season.SPRING -> SeasonOf(Season.SUMMER, year)
                Season.SUMMER -> SeasonOf(Season.AUTUMN, year)
                Season.AUTUMN -> SeasonOf(Season.WINTER, year)
            }
    }

    data class Year(val year: Int) : CalendarPeriod {
        override val start: LocalDate get() = LocalDate.of(year, 1, 1)
        override val end: LocalDate get() = LocalDate.of(year + 1, 1, 1)
        override val key: String get() = "year:$year"

        override fun previous() = Year(year - 1)

        override fun next() = Year(year + 1)
    }

    companion object {
        private const val MONTHS_IN_SEASON = 3L

        fun weekOf(date: LocalDate) = Week(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))

        fun monthOf(date: LocalDate) = Month(YearMonth.from(date))

        fun seasonOf(date: LocalDate): SeasonOf =
            when (date.month) {
                MonthOfYear.DECEMBER -> SeasonOf(Season.WINTER, date.year)
                MonthOfYear.JANUARY, MonthOfYear.FEBRUARY -> SeasonOf(Season.WINTER, date.year - 1)
                MonthOfYear.MARCH, MonthOfYear.APRIL, MonthOfYear.MAY -> SeasonOf(Season.SPRING, date.year)
                MonthOfYear.JUNE, MonthOfYear.JULY, MonthOfYear.AUGUST -> SeasonOf(Season.SUMMER, date.year)
                else -> SeasonOf(Season.AUTUMN, date.year)
            }

        fun yearOf(date: LocalDate) = Year(date.year)

        /** The period of a [CalendarPeriod.key]; null for anything else. */
        fun fromKey(key: String?): CalendarPeriod? {
            val (type, value) = key?.split(':', limit = 2)?.takeIf { it.size == 2 } ?: return null
            return runCatching {
                when (type) {
                    "week" -> LocalDate.parse(value).takeIf { it.dayOfWeek == DayOfWeek.MONDAY }?.let(::Week)
                    "month" -> Month(YearMonth.parse(value))
                    "season" -> seasonFromKey(value)
                    "year" -> Year(value.toInt())
                    else -> null
                }
            }.getOrNull()
        }

        private fun seasonFromKey(value: String): SeasonOf? {
            val (year, season) = value.split('-', limit = 2).takeIf { it.size == 2 } ?: return null
            return Season.entries.firstOrNull { it.key == season }?.let { SeasonOf(it, year.toInt()) }
        }
    }
}

/** The range of `listen_event.started_at` of [this] period in [zone]. */
fun CalendarPeriod.rangeIn(zone: ZoneId): StatsRange =
    StatsRange(
        start.atStartOfDay(zone).toInstant().toEpochMilli(),
        end.atStartOfDay(zone).toInstant().toEpochMilli(),
    )

/**
 * When the seasonal playlists and the summaries of a season or a year show on the home screen (tasks 2.5.9, 2.5.13,
 * owner's decision): from the last [DAYS_BEFORE_END] days of the period (for a year from December 25) for
 * [SHOWN_DAYS] days. Until the period ends the playlist is rebuilt every day, then it is frozen.
 */
object SeasonalWindow {
    const val DAYS_BEFORE_END = 7L
    const val SHOWN_DAYS = 14L

    /** The first day the period shows on the home screen. */
    fun firstShownDay(period: CalendarPeriod): LocalDate = period.end.minusDays(DAYS_BEFORE_END)

    fun isShown(
        period: CalendarPeriod,
        today: LocalDate,
    ): Boolean {
        val first = firstShownDay(period)
        return !today.isBefore(first) && today.isBefore(first.plusDays(SHOWN_DAYS))
    }

    /** The season and the year shown on [today], the season first; often nothing. */
    fun shownOn(today: LocalDate): List<CalendarPeriod> {
        val season = CalendarPeriod.seasonOf(today)
        val year = CalendarPeriod.yearOf(today)
        return listOf(season, season.previous(), year, year.previous()).filter { isShown(it, today) }
    }

    /**
     * Whether a set of [period] built on [builtDay] (epoch day, null when there is none) must be built on [today]:
     * every day until the period ends, then once more after its end so the listens of the last evening count, then
     * never again.
     */
    fun needsBuild(
        period: CalendarPeriod,
        builtDay: Long?,
        today: Long,
    ): Boolean = builtDay == null || (builtDay != today && builtDay < period.end.toEpochDay())
}
