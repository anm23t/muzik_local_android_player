package org.muzzzlo.player.feature.settings

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import org.muzzzlo.player.core.model.AutoBackupScheduler
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** [AutoBackupScheduler] on WorkManager: one weekly job that survives restarts and waits for a charged battery. */
class WorkManagerAutoBackupScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AutoBackupScheduler {
        private val work get() = WorkManager.getInstance(context)

        override fun schedule(enabled: Boolean) {
            if (!enabled) {
                work.cancelUniqueWork(WEEKLY)
                return
            }
            val request =
                PeriodicWorkRequestBuilder<AutoBackupWorker>(DAYS_PER_WEEK, TimeUnit.DAYS)
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                    .build()
            work.enqueueUniquePeriodicWork(WEEKLY, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        override fun runNow() {
            val request = OneTimeWorkRequestBuilder<AutoBackupWorker>().setInputData(AutoBackupWorker.forced()).build()
            work.enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        }

        private companion object {
            const val WEEKLY = "auto-backup-weekly"
            const val NOW = "auto-backup-now"
            const val DAYS_PER_WEEK = 7L
        }
    }
