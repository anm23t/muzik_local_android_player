package org.muzzzlo.player.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import org.muzzzlo.player.core.designsystem.component.MuzzzloDialog
import org.muzzzlo.player.core.designsystem.component.RadioOptionRow
import org.muzzzlo.player.core.designsystem.component.StepperRow
import org.muzzzlo.player.core.designsystem.component.SwitchRow
import org.muzzzlo.player.core.designsystem.component.ValueRow
import org.muzzzlo.player.core.model.ReplayGainMode
import org.muzzzlo.player.core.model.ReplayGainSettings

/** ReplayGain rows of the playback section: mode, preamp, gain for untagged files and clip protection. */
@Composable
internal fun ReplayGainRows(
    replayGain: ReplayGainSettings,
    viewModel: SettingsViewModel,
) {
    ValueRow(
        text = stringResource(R.string.settings_replay_gain),
        onClick = { viewModel.onDialogRequested(SettingsDialog.ReplayGainMode) },
        value = modeLabel(replayGain.mode),
        secondary = stringResource(R.string.settings_replay_gain_hint),
    )
    ValueRow(
        text = stringResource(R.string.settings_replay_gain_preamp),
        onClick = { viewModel.onDialogRequested(SettingsDialog.Preamp) },
        value = decibelLabel(replayGain.preampDb),
        secondary = stringResource(R.string.settings_replay_gain_preamp_hint),
    )
    ValueRow(
        text = stringResource(R.string.settings_replay_gain_untagged),
        onClick = { viewModel.onDialogRequested(SettingsDialog.UntaggedGain) },
        value = decibelLabel(replayGain.untaggedGainDb),
        secondary = stringResource(R.string.settings_replay_gain_untagged_hint),
    )
    SwitchRow(
        text = stringResource(R.string.settings_replay_gain_clipping),
        checked = replayGain.clipProtection,
        onCheckedChange = viewModel::onClipProtectionChanged,
        secondary = stringResource(R.string.settings_replay_gain_clipping_hint),
    )
}

@Composable
internal fun ReplayGainModeDialog(
    replayGain: ReplayGainSettings,
    onChosen: (ReplayGainMode) -> Unit,
    onDismiss: () -> Unit,
) {
    MuzzzloDialog(stringResource(R.string.settings_replay_gain), onDismiss) {
        ReplayGainMode.entries.forEach { mode ->
            RadioOptionRow(
                text = modeLabel(mode),
                selected = mode == replayGain.mode,
                onClick = { onChosen(mode) },
                secondary = modeNote(mode),
                secondaryMaxLines = 2,
            )
        }
    }
}

/** A decibel value changed with minus and plus in steps of [ReplayGainSettings.STEP_DB]. */
@Composable
internal fun DecibelDialog(
    title: String,
    db: Float,
    range: ClosedFloatingPointRange<Float>,
    onStep: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    MuzzzloDialog(title, onDismiss) {
        StepperRow(
            value = decibelLabel(db),
            onDecrease = { onStep(-1) },
            onIncrease = { onStep(1) },
            decreaseDescription = stringResource(R.string.settings_decrease),
            increaseDescription = stringResource(R.string.settings_increase),
            canDecrease = db > range.start,
            canIncrease = db < range.endInclusive,
        )
    }
}

@Composable
private fun modeLabel(mode: ReplayGainMode): String =
    when (mode) {
        ReplayGainMode.OFF -> stringResource(R.string.settings_off)
        ReplayGainMode.TRACK -> stringResource(R.string.settings_replay_gain_track)
        ReplayGainMode.ALBUM -> stringResource(R.string.settings_replay_gain_album)
        ReplayGainMode.AUTO -> stringResource(R.string.settings_replay_gain_auto)
    }

@Composable
private fun modeNote(mode: ReplayGainMode): String? =
    when (mode) {
        ReplayGainMode.OFF -> null
        ReplayGainMode.TRACK -> stringResource(R.string.settings_replay_gain_track_note)
        ReplayGainMode.ALBUM -> stringResource(R.string.settings_replay_gain_album_note)
        ReplayGainMode.AUTO -> stringResource(R.string.settings_replay_gain_auto_note)
    }

@Composable
private fun decibelLabel(db: Float): String {
    val locale = LocalConfiguration.current.locales[0]
    return stringResource(R.string.settings_decibels, formatDecibels(db, locale))
}
