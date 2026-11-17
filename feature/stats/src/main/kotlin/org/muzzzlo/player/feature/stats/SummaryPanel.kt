package org.muzzzlo.player.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.ChoiceChip
import org.muzzzlo.player.core.designsystem.component.OutlinedPanel
import org.muzzzlo.player.core.designsystem.component.PillButton
import org.muzzzlo.player.core.designsystem.component.RoundIconButton
import org.muzzzlo.player.core.designsystem.component.RoundIconButtonSize
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.designsystem.R as DesignR

/**
 * The summary button of the statistics screen (task 2.5.13, no mockup, DESIGN.md, section 7): a framed panel as the
 * totals, "Week / Month" chips, the period between arrows (the current one by default, no arrow into the future) and
 * "Open", which is off while the period has no listens.
 *
 * @param onOpen opens the slides of the period with this `CalendarPeriod.key`.
 */
@Composable
internal fun SummaryPanel(
    onOpen: (String) -> Unit,
    viewModel: SummaryPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OutlinedPanel {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.SmallGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.summary_button_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = MuzzzloColors.TextPrimary,
            )
            SummaryKind.entries.forEach { kind ->
                ChoiceChip(
                    text =
                        stringResource(
                            if (kind == SummaryKind.WEEK) R.string.summary_kind_week else R.string.summary_kind_month,
                        ),
                    selected = kind == state.kind,
                    onClick = { viewModel.onKindChosen(kind) },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.ItemGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(
                icon = DesignR.drawable.ic_chevron_left,
                contentDescription = stringResource(R.string.summary_previous),
                onClick = viewModel::onPrevious,
                size = RoundIconButtonSize.Small,
            )
            Text(
                text = periodTitle(state.period),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MuzzzloColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            // The back chevron mirrored: the icon set has no right one of this size.
            RoundIconButton(
                icon = DesignR.drawable.ic_chevron_left,
                contentDescription = stringResource(R.string.summary_next),
                onClick = viewModel::onNext,
                modifier = Modifier.scale(scaleX = -1f, scaleY = 1f),
                size = RoundIconButtonSize.Small,
                enabled = state.canGoNext,
            )
            PillButton(
                text = stringResource(R.string.summary_open),
                onClick = { onOpen(state.period.key) },
                enabled = state.hasListens,
            )
        }
    }
}
