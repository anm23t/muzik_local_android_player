package org.muzzzlo.player.core.playback.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.playback.MediaControllerPlayer
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PlaybackModule {
    @Binds
    @Singleton
    abstract fun controller(impl: MediaControllerPlayer): PlayerController
}
