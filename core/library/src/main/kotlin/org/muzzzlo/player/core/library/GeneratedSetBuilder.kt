package org.muzzzlo.player.core.library

import org.muzzzlo.player.core.model.CalendarPeriod
import org.muzzzlo.player.core.model.CoverGradients
import org.muzzzlo.player.core.model.GeneratedInput
import org.muzzzlo.player.core.model.GeneratedKind
import org.muzzzlo.player.core.model.GeneratedSets
import org.muzzzlo.player.core.model.SeasonalWindow
import java.time.LocalDate
import javax.inject.Inject

/** One built set: its tracks in order and its gradient cover. */
internal data class BuiltSet(
    val trackIds: List<Long>,
    val gradient: Int,
)

/** The stored set of a seasonal kind: its period key and the day it was built on. */
internal data class StoredSeasonal(
    val period: String?,
    val day: Long,
)

/** Picks the rules and the gradient of each kind of generated playlist (task 2.5.7). */
internal class GeneratedSetBuilder
    @Inject
    constructor() {
        /** The kinds built every day, in the order of the home screen. */
        val kinds: List<GeneratedKind> = GeneratedKind.daily

        fun build(
            kind: GeneratedKind,
            input: GeneratedInput,
            period: CalendarPeriod? = null,
        ): BuiltSet = BuiltSet(GeneratedSets.build(kind, input), CoverGradients.forKind(kind, input.epochDay, period))

        /**
         * The seasonal sets to build on [today] (task 2.5.9): the season and the year in their window
         * ([SeasonalWindow.shownOn]) whose stored set ([stored] by kind key) is of another period or still needs a
         * build ([SeasonalWindow.needsBuild]).
         */
        fun seasonalToBuild(
            stored: Map<String, StoredSeasonal>,
            today: LocalDate,
        ): List<Pair<GeneratedKind, CalendarPeriod>> =
            SeasonalWindow.shownOn(today).mapNotNull { period ->
                val kind = seasonalKindOf(period) ?: return@mapNotNull null
                val set = stored[kind.key]?.takeIf { it.period == period.key }
                (kind to period).takeIf { SeasonalWindow.needsBuild(period, set?.day, today.toEpochDay()) }
            }

        /**
         * The kinds to build now: those without a set of [today] ([storedDays] maps a kind key to the day of its
         * set) and those whose set of today came out empty ([emptyKinds]), because the first run of a day may build
         * before the first scan ends. A full set of today is kept, so the set does not change during the day.
         */
        fun staleKinds(
            storedDays: Map<String, Long>,
            emptyKinds: Set<String>,
            today: Long,
        ): List<GeneratedKind> = kinds.filter { storedDays[it.key] != today || it.key in emptyKinds }

        companion object {
            /** The seasonal kind of a [period]: a season or a year; weeks and months have no playlist. */
            fun seasonalKindOf(period: CalendarPeriod): GeneratedKind? =
                when (period) {
                    is CalendarPeriod.SeasonOf -> GeneratedKind.MOST_PLAYED_SEASON
                    is CalendarPeriod.Year -> GeneratedKind.MOST_PLAYED_YEAR
                    else -> null
                }

            const val ON_REPEAT_MS = GeneratedSets.ON_REPEAT_DAYS * 24L * 60 * 60 * 1000
        }
    }
