package org.muzzzlo.player.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.muzzzlo.player.core.designsystem.R

private const val WINTER = 0
private const val SPRING = 1
private const val SUMMER = 2
private const val YEARS_IN_CENTURY = 100

/**
 * The name of a calendar season (tasks 2.5.9, 2.5.12): "Autumn 2026", winter with two years ("Winter 2026–27").
 * The design system does not know `core:model`, so [season] is the ordinal of its `Season`: 0 winter, 1 spring,
 * 2 summer, 3 autumn; [year] is the year the season starts in.
 */
@Composable
fun seasonName(
    season: Int,
    year: Int,
): String =
    when (season) {
        WINTER -> stringResource(R.string.period_season_winter, year, (year + 1) % YEARS_IN_CENTURY)
        SPRING -> stringResource(R.string.period_season_spring, year)
        SUMMER -> stringResource(R.string.period_season_summer, year)
        else -> stringResource(R.string.period_season_autumn, year)
    }
