package org.muzzzlo.player.core.stats.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.muzzzlo.player.core.model.ListenRecorder
import org.muzzzlo.player.core.stats.DefaultListenRecorder

@Module
@InstallIn(SingletonComponent::class)
internal abstract class StatsModule {
    @Binds
    abstract fun listenRecorder(impl: DefaultListenRecorder): ListenRecorder
}
