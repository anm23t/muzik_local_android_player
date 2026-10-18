package org.muzzzlo.player.core.library.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.muzzzlo.player.core.library.DefaultLibraryRepository
import org.muzzzlo.player.core.model.LibraryRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LibraryModule {
    @Binds
    @Singleton
    abstract fun repository(impl: DefaultLibraryRepository): LibraryRepository
}
