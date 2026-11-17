package org.muzzzlo.player.feature.stats

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import org.muzzzlo.player.core.designsystem.component.seasonName
import org.muzzzlo.player.core.model.CalendarPeriod
import java.time.format.TextStyle

/** The name of a period (task 2.5.12): "Week 47", "November 2026", "Autumn 2026", "2026". */
@Composable
internal fun periodTitle(period: CalendarPeriod): String =
    when (period) {
        is CalendarPeriod.Week -> stringResource(R.string.summary_week, period.number)
        is CalendarPeriod.Month -> {
            val locale = LocalConfiguration.current.locales[0]
            val month = period.month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
            "${month.replaceFirstChar { it.titlecase(locale) }} ${period.month.year}"
        }
        is CalendarPeriod.SeasonOf -> seasonName(period.season.ordinal, period.year)
        is CalendarPeriod.Year -> period.year.toString()
    }
