package org.muzzzlo.player.feature.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.muzzzlo.player.core.model.AutoBackupRepository
import org.muzzzlo.player.core.model.AutoBackupScheduler
import org.muzzzlo.player.core.model.ReleaseSource
import org.muzzzlo.player.core.model.SettingsRepository
import org.muzzzlo.player.core.model.UpdateCheckStore
import org.muzzzlo.player.core.model.UpdateChecker
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SettingsModule {
    @Binds
    @Singleton
    abstract fun repository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun autoBackupRepository(impl: DataStoreAutoBackupRepository): AutoBackupRepository

    @Binds
    @Singleton
    abstract fun updateCheckStore(impl: DataStoreUpdateCheckStore): UpdateCheckStore

    @Binds
    abstract fun releaseSource(impl: GithubReleaseSource): ReleaseSource

    @Binds
    abstract fun autoBackupScheduler(impl: WorkManagerAutoBackupScheduler): AutoBackupScheduler

    @Binds
    abstract fun backupFiles(impl: ContentResolverBackupFiles): BackupFiles

    companion object {
        @Provides
        @Singleton
        fun dataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> = context.settingsDataStore

        @Provides
        fun updateChecker(
            @ApplicationContext context: Context,
            store: UpdateCheckStore,
            source: ReleaseSource,
            clock: Clock,
        ): UpdateChecker = UpdateChecker(store, source, currentVersion(context)) { clock.millis() }

        private fun currentVersion(context: Context): String =
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
}
