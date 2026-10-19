package org.muzzzlo.player.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.muzzzlo.player.core.designsystem.component.MuzzzloDialog
import org.muzzzlo.player.core.designsystem.component.StepperRow
import org.muzzzlo.player.core.designsystem.component.SwitchRow
import org.muzzzlo.player.core.designsystem.component.ValueRow
import org.muzzzlo.player.core.model.CrossfadeSettings

/**
 * Crossfade rows of the playback section: the length, and whether consecutive tracks of an album are crossfaded
 * too. The second row is shown only while crossfade is on.
 */
@Composable
internal fun CrossfadeRows(
    crossfade: CrossfadeSettings,
    viewModel: SettingsViewModel,
) {
    ValueRow(
        text = stringResource(R.string.settings_crossfade),
        onClick = { viewModel.onDialogRequested(SettingsDialog.Crossfade) },
        value = crossfadeLabel(crossfade.seconds),
        secondary = stringResource(R.string.settings_crossfade_hint),
    )
    if (crossfade.seconds > 0) {
        SwitchRow(
            text = stringResource(R.string.settings_crossfade_within_album),
            checked = crossfade.withinAlbum,
            onCheckedChange = viewModel::onCrossfadeWithinAlbumChanged,
            secondary = stringResource(R.string.settings_crossfade_within_album_hint),
        )
    }
}

/** The crossfade length changed with minus and plus in steps of one second; 0 is "off". */
@Composable
internal fun CrossfadeDialog(
    seconds: Int,
    onStep: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    MuzzzloDialog(stringResource(R.string.settings_crossfade), onDismiss) {
        StepperRow(
            value = crossfadeLabel(seconds),
            onDecrease = { onStep(-1) },
            onIncrease = { onStep(1) },
            decreaseDescription = stringResource(R.string.settings_decrease),
            increaseDescription = stringResource(R.string.settings_increase),
            canDecrease = seconds > 0,
            canIncrease = seconds < CrossfadeSettings.MAX_SECONDS,
        )
    }
}

@Composable
private fun crossfadeLabel(seconds: Int): String =
    if (seconds == 0) stringResource(R.string.settings_off) else stringResource(R.string.settings_seconds, seconds)
