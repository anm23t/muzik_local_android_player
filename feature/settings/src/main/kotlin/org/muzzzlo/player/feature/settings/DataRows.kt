package org.muzzzlo.player.feature.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.MuzzzloDialog
import org.muzzzlo.player.core.designsystem.component.SwitchRow
import org.muzzzlo.player.core.designsystem.component.ValueRow
import org.muzzzlo.player.core.model.AutoBackupSettings
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

/** Rows of the "Data" section: manual export and import. The files are chosen with the system picker. */
@Composable
internal fun DataRows(viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val exportBackup =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) viewModel.onExportBackup(uri.toString())
        }
    val exportHistory =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) viewModel.onExportHistory(uri.toString())
        }
    val import =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.onImport(uri.toString())
        }
    val working = if (state.busy) stringResource(R.string.settings_working) else null
    ValueRow(
        text = stringResource(R.string.settings_export_backup),
        onClick = { exportBackup.launch("muzzzlo-backup-$today.json") },
        value = working,
        chevron = false,
        secondary = stringResource(R.string.settings_export_backup_hint),
    )
    ValueRow(
        text = stringResource(R.string.settings_import_backup),
        onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        chevron = false,
        secondary = stringResource(R.string.settings_import_backup_hint),
    )
    ValueRow(
        text = stringResource(R.string.settings_export_history),
        onClick = { exportHistory.launch("muzzzlo-history-$today.csv") },
        chevron = false,
        secondary = stringResource(R.string.settings_export_history_hint),
    )
    AutoBackupRows(state.auto, viewModel)
    state.message?.let { BackupMessageDialog(it, viewModel::onMessageDismissed) }
}

/** The weekly copy: a switch (it asks for a folder first), the folder and "back up now". */
@Composable
private fun AutoBackupRows(
    auto: AutoBackupSettings,
    viewModel: BackupViewModel,
) {
    val context = LocalContext.current
    var enableAfterPick by rememberSaveable { mutableStateOf(false) }
    val pickFolder =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                viewModel.onFolderChosen(uri.toString(), enable = enableAfterPick)
            }
            enableAfterPick = false
        }
    SwitchRow(
        text = stringResource(R.string.settings_auto_backup),
        checked = auto.enabled,
        onCheckedChange = { enabled ->
            if (enabled && auto.folderUri == null) {
                enableAfterPick = true
                pickFolder.launch(null)
            } else {
                viewModel.onAutoBackupChanged(enabled)
            }
        },
        secondary = autoBackupStatus(auto),
    )
    ValueRow(
        text = stringResource(R.string.settings_auto_backup_folder),
        onClick = { pickFolder.launch(null) },
        value = auto.folderUri?.let(::folderLabel) ?: stringResource(R.string.settings_auto_backup_no_folder),
    )
    if (auto.folderUri != null) {
        ValueRow(
            text = stringResource(R.string.settings_auto_backup_now),
            onClick = viewModel::onBackUpNow,
            chevron = false,
        )
    }
}

@Composable
private fun autoBackupStatus(auto: AutoBackupSettings): String {
    val lastRun = auto.lastRunMs ?: return stringResource(R.string.settings_auto_backup_hint)
    val time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(lastRun))
    return stringResource(
        if (auto.lastRunFailed) R.string.settings_auto_backup_failed else R.string.settings_auto_backup_done,
        time,
    )
}

@Composable
private fun BackupMessageDialog(
    message: BackupMessage,
    onDismiss: () -> Unit,
) {
    MuzzzloDialog(stringResource(R.string.settings_backup_title), onDismiss) {
        Text(stringResource(messageText(message)), style = MaterialTheme.typography.bodyLarge)
        if (message is BackupMessage.Imported) {
            val result = message.result
            Text(
                stringResource(
                    R.string.settings_backup_imported,
                    result.events,
                    result.marks,
                    result.aliases,
                    result.compounds,
                ),
            )
            if (result.playlists > 0) Text(stringResource(R.string.settings_backup_playlists, result.playlists))
            if (result.unmatchedMarks > 0) {
                Text(stringResource(R.string.settings_backup_unmatched, result.unmatchedMarks))
            }
            if (result.unmatchedPlaylistTracks > 0) {
                Text(stringResource(R.string.settings_backup_unmatched_playlist_tracks, result.unmatchedPlaylistTracks))
            }
            if (result.aliases > 0) Text(stringResource(R.string.settings_backup_rescan_hint))
        }
    }
}

private fun messageText(message: BackupMessage): Int =
    when (message) {
        BackupMessage.BackupSaved -> R.string.settings_backup_saved
        BackupMessage.HistorySaved -> R.string.settings_history_saved
        is BackupMessage.Imported -> R.string.settings_backup_imported_title
        BackupMessage.NotABackup -> R.string.settings_backup_not_a_backup
        BackupMessage.FileError -> R.string.settings_backup_file_error
    }
