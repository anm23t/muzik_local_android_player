package org.muzzzlo.player.core.library.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.muzzzlo.player.core.library.DefaultAccentRepository
import org.muzzzlo.player.core.library.DefaultArtistCheckRepository
import org.muzzzlo.player.core.library.DefaultArtistProfileRepository
import org.muzzzlo.player.core.library.DefaultAutoPlaylistRepository
import org.muzzzlo.player.core.library.DefaultCompoundRepository
import org.muzzzlo.player.core.library.DefaultCoverRepository
import org.muzzzlo.player.core.library.DefaultLibraryMaintenance
import org.muzzzlo.player.core.library.DefaultLibraryRepository
import org.muzzzlo.player.core.library.DefaultPlaylistRepository
import org.muzzzlo.player.core.library.DefaultPlaylistTransfer
import org.muzzzlo.player.core.library.DefaultSearchRepository
import org.muzzzlo.player.core.library.DefaultTrackMarksRepository
import org.muzzzlo.player.core.library.DefaultTrackTagRepository
import org.muzzzlo.player.core.library.MediaStoreTrackFiles
import org.muzzzlo.player.core.library.TrackFiles
import org.muzzzlo.player.core.model.AccentRepository
import org.muzzzlo.player.core.model.ArtistCheckRepository
import org.muzzzlo.player.core.model.ArtistProfileRepository
import org.muzzzlo.player.core.model.AutoPlaylistRepository
import org.muzzzlo.player.core.model.CompoundRepository
import org.muzzzlo.player.core.model.CoverRepository
import org.muzzzlo.player.core.model.LibraryMaintenance
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.PlaylistRepository
import org.muzzzlo.player.core.model.PlaylistTransfer
import org.muzzzlo.player.core.model.SearchRepository
import org.muzzzlo.player.core.model.TrackMarksRepository
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
    abstract fun search(impl: DefaultSearchRepository): SearchRepository

    @Binds
    @Singleton
    abstract fun trackTags(impl: DefaultTrackTagRepository): TrackTagRepository

    @Binds
    @Singleton
    abstract fun artistProfiles(impl: DefaultArtistProfileRepository): ArtistProfileRepository

    @Binds
    @Singleton
    abstract fun artistChecks(impl: DefaultArtistCheckRepository): ArtistCheckRepository

    @Binds
    @Singleton
    abstract fun compounds(impl: DefaultCompoundRepository): CompoundRepository

    @Binds
    @Singleton
    abstract fun covers(impl: DefaultCoverRepository): CoverRepository

    @Binds
    @Singleton
    abstract fun accents(impl: DefaultAccentRepository): AccentRepository

    @Binds
    @Singleton
    abstract fun playlists(impl: DefaultPlaylistRepository): PlaylistRepository

    @Binds
    abstract fun playlistTransfer(impl: DefaultPlaylistTransfer): PlaylistTransfer

    @Binds
    @Singleton
    abstract fun autoPlaylists(impl: DefaultAutoPlaylistRepository): AutoPlaylistRepository

    @Binds
    @Singleton
    abstract fun trackMarks(impl: DefaultTrackMarksRepository): TrackMarksRepository

    @Binds
    abstract fun maintenance(impl: DefaultLibraryMaintenance): LibraryMaintenance
}
