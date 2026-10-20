package org.muzzzlo.player.core.database.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.muzzzlo.player.core.database.backup.DefaultBackupRepository
import org.muzzzlo.player.core.model.BackupRepository

@Module
@InstallIn(SingletonComponent::class)
internal abstract class BackupModule {
    @Binds
    abstract fun backupRepository(impl: DefaultBackupRepository): BackupRepository
}
