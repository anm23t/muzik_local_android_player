package org.muzzzlo.player.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.muzzzlo.player.core.designsystem.component.MuzzzloDialog
import org.muzzzlo.player.core.designsystem.component.StepperRow
import org.muzzzlo.player.core.designsystem.component.SwitchRow
import org.muzzzlo.player.core.designsystem.component.ValueRow
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.model.StatsSettings

/** Rows of the statistics section: the listen threshold and the private mode. */
@Composable
internal fun StatsRows(
    stats: StatsSettings,
    viewModel: SettingsViewModel,
) {
    ValueRow(
        text = stringResource(R.string.settings_listen_threshold),
        onClick = { viewModel.onDialogRequested(SettingsDialog.ListenThreshold) },
        value =
            stringResource(R.string.settings_listen_threshold_value, stats.thresholdPercent, stats.thresholdMinutes),
        secondary = stringResource(R.string.settings_listen_threshold_hint),
    )
    SwitchRow(
        text = stringResource(R.string.settings_private_mode),
        checked = stats.privateMode,
        onCheckedChange = viewModel.stats::onPrivateModeChanged,
        secondary = stringResource(R.string.settings_private_mode_hint),
    )
}

/** The share of the track and the time, each changed with minus and plus; whichever comes first counts. */
@Composable
internal fun ListenThresholdDialog(
    stats: StatsSettings,
    onPercentStep: (Int) -> Unit,
    onMinutesStep: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    MuzzzloDialog(stringResource(R.string.settings_listen_threshold), onDismiss) {
        StepperLabel(stringResource(R.string.settings_threshold_share))
        StepperRow(
            value = stringResource(R.string.settings_percent, stats.thresholdPercent),
            onDecrease = { onPercentStep(-1) },
            onIncrease = { onPercentStep(1) },
            decreaseDescription = stringResource(R.string.settings_decrease),
            increaseDescription = stringResource(R.string.settings_increase),
            canDecrease = stats.thresholdPercent > StatsSettings.MIN_PERCENT,
            canIncrease = stats.thresholdPercent < StatsSettings.MAX_PERCENT,
        )
        StepperLabel(stringResource(R.string.settings_threshold_time))
        StepperRow(
            value = stringResource(R.string.settings_minutes, stats.thresholdMinutes),
            onDecrease = { onMinutesStep(-1) },
            onIncrease = { onMinutesStep(1) },
            decreaseDescription = stringResource(R.string.settings_decrease),
            increaseDescription = stringResource(R.string.settings_increase),
            canDecrease = stats.thresholdMinutes > StatsSettings.MIN_MINUTES,
            canIncrease = stats.thresholdMinutes < StatsSettings.MAX_MINUTES,
        )
    }
}

@Composable
private fun StepperLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MuzzzloColors.TextSecondary)
}
