package org.muzzzlo.player.core.playback.di

import androidx.media3.common.util.UnstableApi
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.WaveformRepository
import org.muzzzlo.player.core.playback.DefaultWaveformRepository
import org.muzzzlo.player.core.playback.MediaControllerPlayer
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PlaybackModule {
    @Binds
    @Singleton
    abstract fun controller(impl: MediaControllerPlayer): PlayerController

    @UnstableApi
    @Binds
    abstract fun waveforms(impl: DefaultWaveformRepository): WaveformRepository
}
