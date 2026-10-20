package org.muzzzlo.player.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.MuzzzloDialog
import org.muzzzlo.player.core.designsystem.component.RadioOptionRow
import org.muzzzlo.player.core.designsystem.component.ScreenFrame
import org.muzzzlo.player.core.designsystem.component.SectionTitle
import org.muzzzlo.player.core.designsystem.component.SwitchRow
import org.muzzzlo.player.core.designsystem.component.ValueRow
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.model.AppLanguage
import org.muzzzlo.player.core.model.AppSettings
import org.muzzzlo.player.core.model.ReplayGainSettings

/**
 * Settings (no mockup, DESIGN.md, section 7): sections of rows. A row either shows its value and opens a choice
 * dialog, flips a switch, or opens a screen of its own (folders, quarantine, compound names).
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenFolders: () -> Unit,
    onOpenQuarantine: () -> Unit,
    onOpenCompoundNames: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings
    ScreenFrame(modifier) {
        SettingsHeader(onBack)
        SectionTitle(stringResource(R.string.settings_title))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.ContentGap),
        ) {
            GroupTitle(R.string.settings_group_library)
            ValueRow(stringResource(R.string.settings_folders), onOpenFolders, secondary = foldersSummary(settings))
            ValueRow(
                text = stringResource(R.string.settings_min_length),
                onClick = { viewModel.onDialogRequested(SettingsDialog.MinLength) },
                value = lengthLabel(settings.minTrackSeconds),
                secondary = stringResource(R.string.settings_min_length_hint),
            )
            ValueRow(
                text = stringResource(R.string.settings_rescan),
                onClick = viewModel::onRescanClicked,
                value = if (state.isScanning) stringResource(R.string.settings_scanning) else null,
                chevron = false,
            )
            ValueRow(
                text = stringResource(R.string.settings_quarantine),
                onClick = onOpenQuarantine,
                value = state.quarantineCount.takeIf { it > 0 }?.toString(),
                secondary = stringResource(R.string.settings_quarantine_hint),
            )

            GroupTitle(R.string.settings_group_tags)
            ValueRow(
                text = stringResource(R.string.settings_id3_version),
                onClick = { viewModel.onDialogRequested(SettingsDialog.Id3Version) },
                value = id3Label(settings.id3v2VersionForNewTags),
                secondary = stringResource(R.string.settings_id3_version_hint),
            )
            SwitchRow(
                text = stringResource(R.string.settings_confirm_write),
                checked = settings.confirmBeforeWrite,
                onCheckedChange = viewModel::onConfirmBeforeWriteChanged,
                secondary = stringResource(R.string.settings_confirm_write_hint),
            )
            ValueRow(stringResource(R.string.settings_compound_names), onOpenCompoundNames)

            GroupTitle(R.string.settings_group_playback)
            SwitchRow(
                text = stringResource(R.string.settings_gapless),
                checked = settings.gapless,
                onCheckedChange = viewModel::onGaplessChanged,
                secondary = stringResource(R.string.settings_gapless_hint),
            )
            ReplayGainRows(settings.replayGain, viewModel)
            CrossfadeRows(settings.crossfade, viewModel)
            PlaybackBehaviorRows(settings.playbackBehavior, viewModel)

            GroupTitle(R.string.settings_group_stats)
            StatsRows(settings.stats, viewModel)

            GroupTitle(R.string.settings_group_interface)
            ValueRow(
                text = stringResource(R.string.settings_language),
                onClick = { viewModel.onDialogRequested(SettingsDialog.Language) },
                value = languageLabel(settings.language),
            )
            SwitchRow(
                text = stringResource(R.string.settings_dynamic_accent),
                checked = settings.dynamicAccent,
                onCheckedChange = viewModel::onDynamicAccentChanged,
                secondary = stringResource(R.string.settings_dynamic_accent_hint),
            )
        }
    }
    when (state.dialog) {
        SettingsDialog.MinLength ->
            MinLengthDialog(
                settings,
                viewModel::onMinLengthChosen,
                viewModel::onDialogDismissed,
            )
        SettingsDialog.Id3Version -> Id3Dialog(settings, viewModel::onId3VersionChosen, viewModel::onDialogDismissed)
        SettingsDialog.Language -> LanguageDialog(settings, viewModel::onLanguageChosen, viewModel::onDialogDismissed)
        SettingsDialog.ReplayGainMode ->
            ReplayGainModeDialog(settings.replayGain, viewModel::onReplayGainModeChosen, viewModel::onDialogDismissed)
        SettingsDialog.Preamp ->
            DecibelDialog(
                title = stringResource(R.string.settings_replay_gain_preamp),
                db = settings.replayGain.preampDb,
                range = ReplayGainSettings.PREAMP_MIN_DB..ReplayGainSettings.PREAMP_MAX_DB,
                onStep = viewModel::onPreampStepped,
                onDismiss = viewModel::onDialogDismissed,
            )
        SettingsDialog.UntaggedGain ->
            DecibelDialog(
                title = stringResource(R.string.settings_replay_gain_untagged),
                db = settings.replayGain.untaggedGainDb,
                range = ReplayGainSettings.UNTAGGED_MIN_DB..ReplayGainSettings.UNTAGGED_MAX_DB,
                onStep = viewModel::onUntaggedGainStepped,
                onDismiss = viewModel::onDialogDismissed,
            )
        SettingsDialog.Crossfade ->
            CrossfadeDialog(settings.crossfade.seconds, viewModel::onCrossfadeStepped, viewModel::onDialogDismissed)
        SettingsDialog.TransientFocus ->
            TransientFocusDialog(
                settings.playbackBehavior,
                viewModel::onTransientFocusChosen,
                viewModel::onDialogDismissed,
            )
        SettingsDialog.ListenThreshold ->
            ListenThresholdDialog(
                stats = settings.stats,
                onPercentStep = viewModel.stats::onPercentStepped,
                onMinutesStep = viewModel.stats::onMinutesStepped,
                onDismiss = viewModel::onDialogDismissed,
            )
        null -> Unit
    }
}

@Composable
private fun GroupTitle(title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        color = MuzzzloColors.TextSecondary,
    )
}

@Composable
private fun foldersSummary(settings: AppSettings): String? =
    settings.excludedFolders.size.takeIf { it > 0 }?.let {
        pluralStringResource(R.plurals.settings_folders_excluded, it, it)
    }

@Composable
private fun lengthLabel(seconds: Int): String =
    when {
        seconds == 0 -> stringResource(R.string.settings_off)
        seconds % SECONDS_PER_MINUTE == 0 -> stringResource(R.string.settings_minutes, seconds / SECONDS_PER_MINUTE)
        else -> stringResource(R.string.settings_seconds, seconds)
    }

@Composable
private fun id3Label(version: Int): String = stringResource(R.string.settings_id3_option, version)

@Composable
private fun languageLabel(language: AppLanguage): String =
    when (language) {
        AppLanguage.SYSTEM -> stringResource(R.string.settings_language_system)
        AppLanguage.ENGLISH -> stringResource(R.string.settings_language_english)
        AppLanguage.RUSSIAN -> stringResource(R.string.settings_language_russian)
    }

@Composable
private fun MinLengthDialog(
    settings: AppSettings,
    onChosen: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // A value outside the list (written by another version) stays selectable.
    val options = (MinLengthOptions + settings.minTrackSeconds).distinct().sorted()
    MuzzzloDialog(stringResource(R.string.settings_min_length), onDismiss) {
        options.forEach { seconds ->
            RadioOptionRow(
                lengthLabel(seconds),
                selected = seconds == settings.minTrackSeconds,
                onClick = { onChosen(seconds) },
            )
        }
    }
}

@Composable
private fun Id3Dialog(
    settings: AppSettings,
    onChosen: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    MuzzzloDialog(stringResource(R.string.settings_id3_version), onDismiss) {
        AppSettings.ID3V2_VERSIONS.forEach { version ->
            RadioOptionRow(
                text = id3Label(version),
                selected = version == settings.id3v2VersionForNewTags,
                onClick = { onChosen(version) },
                secondary =
                    stringResource(
                        if (version == ID3V23) R.string.settings_id3_v3_note else R.string.settings_id3_v4_note,
                    ),
            )
        }
    }
}

@Composable
private fun LanguageDialog(
    settings: AppSettings,
    onChosen: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    MuzzzloDialog(stringResource(R.string.settings_language), onDismiss) {
        AppLanguage.entries.forEach { language ->
            RadioOptionRow(
                languageLabel(language),
                selected = language == settings.language,
                onClick = { onChosen(language) },
            )
        }
    }
}

private const val SECONDS_PER_MINUTE = 60
private const val ID3V23 = 3
