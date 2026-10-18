package org.muzzzlo.player.core.library.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.muzzzlo.player.core.library.DefaultCoverRepository
import org.muzzzlo.player.core.library.DefaultLibraryRepository
import org.muzzzlo.player.core.library.DefaultTrackTagRepository
import org.muzzzlo.player.core.library.MediaStoreTrackFiles
import org.muzzzlo.player.core.library.TrackFiles
import org.muzzzlo.player.core.model.CoverRepository
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.TrackTagRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LibraryModule {
    @Binds
    @Singleton
    abstract fun repository(impl: DefaultLibraryRepository): LibraryRepository

    @Binds
    abstract fun trackFiles(impl: MediaStoreTrackFiles): TrackFiles

    @Binds
    @Singleton
    abstract fun trackTags(impl: DefaultTrackTagRepository): TrackTagRepository

    @Binds
    @Singleton
    abstract fun covers(impl: DefaultCoverRepository): CoverRepository
}
