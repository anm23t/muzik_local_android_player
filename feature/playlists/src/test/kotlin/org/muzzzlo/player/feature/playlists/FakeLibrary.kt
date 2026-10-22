package org.muzzzlo.player.feature.playlists

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.muzzzlo.player.core.model.Album
import org.muzzzlo.player.core.model.Artist
import org.muzzzlo.player.core.model.ArtistDiscography
import org.muzzzlo.player.core.model.ArtistRef
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.Track

/** Library that knows only the artists of tracks, which is all the player asks it. */
class FakeLibrary(
    private val artistsByTrack: Map<Long, List<ArtistRef>> = emptyMap(),
) : LibraryRepository {
    override fun observeTracks(): Flow<List<Track>> = emptyFlow()

    override fun observeAlbums() = emptyFlow<List<Album>>()

    override fun observeAlbumTracks(albumId: Long): Flow<List<Track>> = emptyFlow()

    override fun observeArtists(): Flow<List<Artist>> = emptyFlow()

    override fun observeArtistTracks(artistId: Long): Flow<List<Track>> = emptyFlow()

    override fun observeArtistAlbums(artistId: Long): Flow<ArtistDiscography> = emptyFlow()

    override suspend fun artistId(name: String): Long? = null

    override suspend fun artistsOfTrack(trackId: Long): List<ArtistRef> = artistsByTrack[trackId].orEmpty()

    override suspend fun setArtistPhoto(
        artistId: Long,
        sourceUri: String,
    ) = Unit

    override suspend fun removeArtistPhoto(artistId: Long) = Unit

    override suspend fun tracksByIds(ids: List<Long>): List<Track> = emptyList()

    override suspend fun scan() = Unit
}
