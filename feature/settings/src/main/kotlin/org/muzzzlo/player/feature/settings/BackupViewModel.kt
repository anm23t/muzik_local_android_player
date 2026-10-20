package org.muzzzlo.player.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.muzzzlo.player.core.model.AutoBackupRepository
import org.muzzzlo.player.core.model.AutoBackupScheduler
import org.muzzzlo.player.core.model.AutoBackupSettings
import org.muzzzlo.player.core.model.BackupFormatException
import org.muzzzlo.player.core.model.BackupImportResult
import org.muzzzlo.player.core.model.BackupRepository
import java.io.IOException
import javax.inject.Inject

/** What the "Data" section tells the user once an export or an import is over. */
sealed interface BackupMessage {
    data object BackupSaved : BackupMessage

    data object HistorySaved : BackupMessage

    data class Imported(val result: BackupImportResult) : BackupMessage

    /** The chosen file is not a backup of this app, or comes from a newer version. */
    data object NotABackup : BackupMessage

    /** The file could not be read or written. */
    data object FileError : BackupMessage
}

/**
 * State of the "Data" section: [busy] while a file is being processed, [message] until the user closes it,
 * [auto] the automatic backup.
 */
data class BackupUiState(
    val busy: Boolean = false,
    val message: BackupMessage? = null,
    val auto: AutoBackupSettings = AutoBackupSettings(),
)

/** Manual export and import of the backup (ARCHITECTURE.md, section 8); the user picks the files. */
@HiltViewModel
class BackupViewModel
    @Inject
    constructor(
        private val backup: BackupRepository,
        private val files: BackupFiles,
        private val autoBackup: AutoBackupRepository,
        private val scheduler: AutoBackupScheduler,
    ) : ViewModel() {
        private val local = MutableStateFlow(BackupUiState())
        val state: StateFlow<BackupUiState> = local.asStateFlow()

        init {
            viewModelScope.launch {
                autoBackup.settings.collect { auto -> local.update { it.copy(auto = auto) } }
            }
        }

        /** Switches the weekly copy; it needs a folder, so without one nothing changes. */
        fun onAutoBackupChanged(enabled: Boolean) {
            if (enabled && local.value.auto.folderUri == null) return
            viewModelScope.launch {
                autoBackup.setEnabled(enabled)
                scheduler.schedule(enabled)
            }
        }

        /** The folder for the copies; [enable] also turns the weekly copy on (the user came from the switch). */
        fun onFolderChosen(
            uri: String,
            enable: Boolean,
        ) {
            viewModelScope.launch {
                autoBackup.setFolder(uri)
                if (enable) {
                    autoBackup.setEnabled(true)
                    scheduler.schedule(true)
                }
            }
        }

        fun onBackUpNow() {
            if (local.value.auto.folderUri != null) scheduler.runNow()
        }

        fun onExportBackup(uri: String) =
            launchTask {
                files.write(uri, backup.exportJson())
                BackupMessage.BackupSaved
            }

        fun onExportHistory(uri: String) =
            launchTask {
                files.write(uri, backup.exportEventsCsv())
                BackupMessage.HistorySaved
            }

        fun onImport(uri: String) = launchTask { BackupMessage.Imported(backup.importJson(files.read(uri))) }

        fun onMessageDismissed() = local.update { it.copy(message = null) }

        private fun launchTask(action: suspend () -> BackupMessage) {
            if (local.value.busy) return
            local.update { it.copy(busy = true) }
            viewModelScope.launch {
                val message =
                    try {
                        action()
                    } catch (e: BackupFormatException) {
                        BackupMessage.NotABackup
                    } catch (e: IOException) {
                        BackupMessage.FileError
                    } catch (e: SecurityException) {
                        BackupMessage.FileError
                    }
                local.update { it.copy(busy = false, message = message) }
            }
        }
    }
