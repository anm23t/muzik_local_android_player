package org.muzzzlo.player.feature.settings

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.model.AutoBackupFiles
import org.muzzzlo.player.core.model.AutoBackupRepository
import org.muzzzlo.player.core.model.BackupRepository
import org.muzzzlo.player.core.model.DispatcherProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** What the worker needs; WorkManager creates the worker itself, so the objects come from Hilt by hand. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface AutoBackupEntryPoint {
    fun backup(): BackupRepository

    fun autoBackup(): AutoBackupRepository

    fun folder(): BackupFolder

    fun clock(): Clock

    fun dispatchers(): DispatcherProvider
}

/**
 * Saves one automatic copy into the chosen folder. The weekly run does nothing while the feature is off or has
 * no folder; the "back up now" run ([FORCE]) ignores the switch. A failure is recorded for the settings screen and
 * is not retried: the next weekly run tries again.
 */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, AutoBackupEntryPoint::class.java)
        val settings = deps.autoBackup().settings.first()
        val folder = settings.folderUri
        val force = inputData.getBoolean(FORCE, false)
        if (folder == null || !(settings.enabled || force)) return Result.success()
        val clock = deps.clock()
        val failed =
            runCatching {
                val json = deps.backup().exportJson()
                val name = AutoBackupFiles.nameFor(Instant.now(clock).atZone(ZoneId.systemDefault()).toLocalDate())
                withContext(deps.dispatchers().io) { deps.folder().save(folder, name, json) }
            }.exceptionOrNull()?.let { it is java.io.IOException || it is SecurityException || throw it } ?: false
        deps.autoBackup().recordResult(clock.millis(), failed)
        return Result.success()
    }

    companion object {
        const val FORCE = "force"

        fun forced(): Data = Data.Builder().putBoolean(FORCE, true).build()
    }
}
