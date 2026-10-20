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
import org.muzzzlo.player.core.model.SettingsRepository
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
    abstract fun autoBackupScheduler(impl: WorkManagerAutoBackupScheduler): AutoBackupScheduler

    @Binds
    abstract fun backupFiles(impl: ContentResolverBackupFiles): BackupFiles

    companion object {
        @Provides
        @Singleton
        fun dataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> = context.settingsDataStore
    }
}
