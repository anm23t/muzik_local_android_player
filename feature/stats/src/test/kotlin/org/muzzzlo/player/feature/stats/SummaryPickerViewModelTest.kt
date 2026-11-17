package org.muzzzlo.player.feature.stats

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.model.CalendarPeriod
import org.muzzzlo.player.core.model.PeriodSummary
import org.muzzzlo.player.core.model.SummaryRepository
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class SummaryPickerViewModelTest {
    private val today = LocalDate.parse("2027-01-06")
    private val clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).plusHours(10).toInstant(), ZoneOffset.UTC)
    private val withListens = MutableStateFlow<Set<CalendarPeriod>>(emptySet())

    private val summaries =
        object : SummaryRepository {
            override suspend fun summary(period: CalendarPeriod): PeriodSummary? = null

            override fun hasListens(period: CalendarPeriod): Flow<Boolean> = withListens.map { period in it }
        }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `steps back go over the new year`() {
        assertEquals(CalendarPeriod.weekOf(today), summaryPeriod(SummaryKind.WEEK, today, 0))
        assertEquals(CalendarPeriod.Week(LocalDate.parse("2026-12-28")), summaryPeriod(SummaryKind.WEEK, today, 1))
        assertEquals(CalendarPeriod.Month(YearMonth.of(2026, 11)), summaryPeriod(SummaryKind.MONTH, today, 2))
    }

    @Test
    fun `the current week by default, arrows back and forth, no step into the future`() =
        runTest {
            val viewModel = SummaryPickerViewModel(summaries, clock)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

            assertEquals(SummaryKind.WEEK, viewModel.state.value.kind)
            assertEquals(CalendarPeriod.weekOf(today), viewModel.state.value.period)
            assertFalse(viewModel.state.value.canGoNext)

            viewModel.onNext()
            assertEquals(CalendarPeriod.weekOf(today), viewModel.state.value.period)

            viewModel.onPrevious()
            viewModel.onPrevious()
            assertEquals(CalendarPeriod.weekOf(today).previous().previous(), viewModel.state.value.period)
            assertTrue(viewModel.state.value.canGoNext)

            viewModel.onKindChosen(SummaryKind.MONTH)
            assertEquals(CalendarPeriod.monthOf(today), viewModel.state.value.period)
            assertFalse(viewModel.state.value.canGoNext)
        }

    @Test
    fun `open is off for a period without listens`() =
        runTest {
            val viewModel = SummaryPickerViewModel(summaries, clock)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
            assertFalse(viewModel.state.value.hasListens)

            withListens.value = setOf(CalendarPeriod.weekOf(today).previous())
            assertFalse(viewModel.state.value.hasListens)
            viewModel.onPrevious()
            assertTrue(viewModel.state.value.hasListens)
        }
}
