package org.muzzzlo.player.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.muzzzlo.player.core.model.CalendarPeriod
import org.muzzzlo.player.core.model.SummaryRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

/** The periods the summary button of the statistics screen offers (task 2.5.13). */
enum class SummaryKind { WEEK, MONTH }

/** The period [steps] periods before the current [kind] of [today]; 0 is the current one. */
fun summaryPeriod(
    kind: SummaryKind,
    today: LocalDate,
    steps: Int,
): CalendarPeriod {
    var period: CalendarPeriod =
        when (kind) {
            SummaryKind.WEEK -> CalendarPeriod.weekOf(today)
            SummaryKind.MONTH -> CalendarPeriod.monthOf(today)
        }
    repeat(steps) { period = period.previous() }
    return period
}

/**
 * The summary button of the statistics screen.
 *
 * @property canGoNext false on the current period: there is no summary of the future.
 * @property hasListens the period has counted listens; without them the button is off.
 */
data class SummaryPickerState(
    val kind: SummaryKind,
    val period: CalendarPeriod,
    val canGoNext: Boolean,
    val hasListens: Boolean = false,
)

private data class Choice(
    val kind: SummaryKind = SummaryKind.WEEK,
    val steps: Int = 0,
)

/** "Week / Month" and the arrows to past periods of the summary button; the current week by default. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SummaryPickerViewModel
    @Inject
    constructor(
        private val summaries: SummaryRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val choice = MutableStateFlow(Choice())

        val state: StateFlow<SummaryPickerState> =
            choice.flatMapLatest { chosen ->
                val picked = pickerState(chosen)
                summaries.hasListens(picked.period).map { picked.copy(hasListens = it) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), pickerState(Choice()))

        fun onKindChosen(kind: SummaryKind) = choice.update { Choice(kind) }

        fun onPrevious() = choice.update { it.copy(steps = it.steps + 1) }

        fun onNext() = choice.update { it.copy(steps = (it.steps - 1).coerceAtLeast(0)) }

        private fun pickerState(chosen: Choice) =
            SummaryPickerState(
                kind = chosen.kind,
                period = summaryPeriod(chosen.kind, LocalDate.now(clock), chosen.steps),
                canGoNext = chosen.steps > 0,
            )
    }
