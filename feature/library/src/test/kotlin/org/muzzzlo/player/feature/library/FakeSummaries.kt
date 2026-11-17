package org.muzzzlo.player.feature.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.muzzzlo.player.core.model.CalendarPeriod
import org.muzzzlo.player.core.model.PeriodSummary
import org.muzzzlo.player.core.model.SummaryRepository

/** Summaries where a test says which periods have listens. */
class FakeSummaries : SummaryRepository {
    val withListens = MutableStateFlow<Set<CalendarPeriod>>(emptySet())

    override suspend fun summary(period: CalendarPeriod): PeriodSummary? = null

    override fun hasListens(period: CalendarPeriod): Flow<Boolean> = withListens.map { period in it }
}
