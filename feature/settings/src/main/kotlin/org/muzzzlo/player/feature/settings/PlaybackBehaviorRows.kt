package org.muzzzlo.player.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.muzzzlo.player.core.designsystem.component.MuzzzloDialog
import org.muzzzlo.player.core.designsystem.component.RadioOptionRow
import org.muzzzlo.player.core.designsystem.component.SwitchRow
import org.muzzzlo.player.core.designsystem.component.ValueRow
import org.muzzzlo.player.core.model.PlaybackBehaviorSettings
import org.muzzzlo.player.core.model.TransientFocusAction

/** Rows of the playback section for audio focus, headsets and silence at the ends of a track. */
@Composable
internal fun PlaybackBehaviorRows(
    behavior: PlaybackBehaviorSettings,
    viewModel: SettingsViewModel,
) {
    ValueRow(
        text = stringResource(R.string.settings_transient_focus),
        onClick = { viewModel.onDialogRequested(SettingsDialog.TransientFocus) },
        value = actionLabel(behavior.transientFocus),
        secondary = stringResource(R.string.settings_transient_focus_hint),
    )
    SwitchRow(
        text = stringResource(R.string.settings_resume_on_headset),
        checked = behavior.resumeOnHeadset,
        onCheckedChange = viewModel::onResumeOnHeadsetChanged,
        secondary = stringResource(R.string.settings_resume_on_headset_hint),
    )
    SwitchRow(
        text = stringResource(R.string.settings_skip_silence),
        checked = behavior.skipSilence,
        onCheckedChange = viewModel::onSkipSilenceChanged,
        secondary = stringResource(R.string.settings_skip_silence_hint),
    )
}

@Composable
internal fun TransientFocusDialog(
    behavior: PlaybackBehaviorSettings,
    onChosen: (TransientFocusAction) -> Unit,
    onDismiss: () -> Unit,
) {
    MuzzzloDialog(stringResource(R.string.settings_transient_focus), onDismiss) {
        TransientFocusAction.entries.forEach { action ->
            RadioOptionRow(
                text = actionLabel(action),
                selected = action == behavior.transientFocus,
                onClick = { onChosen(action) },
                secondary = actionNote(action),
                secondaryMaxLines = 2,
            )
        }
    }
}

@Composable
private fun actionLabel(action: TransientFocusAction): String =
    when (action) {
        TransientFocusAction.DUCK -> stringResource(R.string.settings_transient_focus_duck)
        TransientFocusAction.PAUSE -> stringResource(R.string.settings_transient_focus_pause)
    }

@Composable
private fun actionNote(action: TransientFocusAction): String =
    when (action) {
        TransientFocusAction.DUCK -> stringResource(R.string.settings_transient_focus_duck_note)
        TransientFocusAction.PAUSE -> stringResource(R.string.settings_transient_focus_pause_note)
    }
