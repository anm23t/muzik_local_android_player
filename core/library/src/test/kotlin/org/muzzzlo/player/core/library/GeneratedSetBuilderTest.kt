package org.muzzzlo.player.core.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.model.CalendarPeriod
import org.muzzzlo.player.core.model.CoverGradients
import org.muzzzlo.player.core.model.GeneratedInput
import org.muzzzlo.player.core.model.GeneratedKind
import org.muzzzlo.player.core.model.Season
import java.time.LocalDate

class GeneratedSetBuilderTest {
    private val builder = GeneratedSetBuilder()
    private val all = GeneratedKind.daily

    @Test
    fun `the first run ever builds every kind`() {
        assertEquals(all, builder.staleKinds(emptyMap(), emptySet(), today = 10))
    }

    @Test
    fun `sets of today are kept during the day`() {
        val stored = all.associate { it.key to 10L }
        assertEquals(emptyList<GeneratedKind>(), builder.staleKinds(stored, emptySet(), today = 10))
    }

    @Test
    fun `a new day rebuilds every set`() {
        val stored = all.associate { it.key to 10L }
        assertEquals(all, builder.staleKinds(stored, emptySet(), today = 11))
    }

    @Test
    fun `an empty set of today is made again`() {
        val stored = all.associate { it.key to 10L }
        val empty = setOf(GeneratedKind.BEST_RATED.key)
        assertEquals(listOf(GeneratedKind.BEST_RATED), builder.staleKinds(stored, empty, today = 10))
    }

    @Test
    fun `a built set carries the gradient of its kind`() {
        val input = GeneratedInput(emptyList(), emptyMap(), nowMs = 0, epochDay = 1)
        all.forEach { assertEquals(CoverGradients.forKind(it, 1), builder.build(it, input).gradient) }
    }

    private val autumn = CalendarPeriod.SeasonOf(Season.AUTUMN, 2026)
    private val season = GeneratedKind.MOST_PLAYED_SEASON

    @Test
    fun `nothing seasonal is built outside the windows`() {
        assertEquals(emptyList<Any>(), builder.seasonalToBuild(emptyMap(), LocalDate.of(2026, 10, 15)))
    }

    @Test
    fun `the season is built in its window and again the next day`() {
        val today = LocalDate.of(2026, 11, 25)
        assertEquals(listOf(season to autumn), builder.seasonalToBuild(emptyMap(), today))
        val builtToday = mapOf(season.key to StoredSeasonal(autumn.key, today.toEpochDay()))
        assertEquals(emptyList<Any>(), builder.seasonalToBuild(builtToday, today))
        assertEquals(listOf(season to autumn), builder.seasonalToBuild(builtToday, today.plusDays(1)))
    }

    @Test
    fun `a set of the last season is replaced, a frozen one is kept`() {
        val today = LocalDate.of(2026, 12, 3)
        val summer = mapOf(season.key to StoredSeasonal("season:2026-summer", 1))
        assertEquals(listOf(season to autumn), builder.seasonalToBuild(summer, today))
        val frozen = mapOf(season.key to StoredSeasonal(autumn.key, LocalDate.of(2026, 12, 1).toEpochDay()))
        assertEquals(emptyList<Any>(), builder.seasonalToBuild(frozen, today))
    }

    @Test
    fun `the year is built from december 25 with its own gradient`() {
        val year = CalendarPeriod.Year(2026)
        val today = LocalDate.of(2026, 12, 25)
        assertEquals(listOf(GeneratedKind.MOST_PLAYED_YEAR to year), builder.seasonalToBuild(emptyMap(), today))
        val input = GeneratedInput(emptyList(), emptyMap(), nowMs = 0, epochDay = 1)
        assertEquals(
            CoverGradients.forPeriod(year),
            builder.build(GeneratedKind.MOST_PLAYED_YEAR, input, year).gradient,
        )
    }
}
