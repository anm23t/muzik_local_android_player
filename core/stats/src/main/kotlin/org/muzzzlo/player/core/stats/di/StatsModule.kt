package org.muzzzlo.player.core.stats.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.muzzzlo.player.core.model.ListenRecorder
import org.muzzzlo.player.core.model.StatsRepository
import org.muzzzlo.player.core.stats.DefaultListenRecorder
import org.muzzzlo.player.core.stats.DefaultStatsRepository
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
internal abstract class StatsModule {
    @Binds
    abstract fun listenRecorder(impl: DefaultListenRecorder): ListenRecorder

    @Binds
    abstract fun statsRepository(impl: DefaultStatsRepository): StatsRepository

    companion object {
        @Provides
        fun clock(): Clock = Clock.systemDefaultZone()
    }
}
