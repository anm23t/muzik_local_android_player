package org.muzzzlo.player.core.model

import kotlinx.coroutines.flow.Flow

/** Read access to the indexed library plus the rescan entry point. */
interface LibraryRepository {
    fun observeTracks(): Flow<List<Track>>

    fun observeAlbums(): Flow<List<Album>>

    fun observeAlbumTracks(albumId: Long): Flow<List<Track>>

    /** Artists with at least one track on the device, in name order. */
    fun observeArtists(): Flow<List<Artist>>

    /** Tracks that credit the artist, in title order. */
    fun observeArtistTracks(artistId: Long): Flow<List<Track>>

    /**
     * Albums of the artist: albums credited to them first, then albums where they only appear on some tracks.
     * Each group goes from the newest year to the oldest.
     */
    fun observeArtistAlbums(artistId: Long): Flow<List<Album>>

    /** Id of the artist with this name (compared by [NameNormalizer.key]), null when there is none. */
    suspend fun artistId(name: String): Long?

    /**
     * Makes a reduced copy of the picture at [sourceUri] (a `content://` URI from the photo picker) the artist's
     * photo. The previous photo is deleted.
     */
    suspend fun setArtistPhoto(
        artistId: Long,
        sourceUri: String,
    )

    /** Deletes the artist's photo. */
    suspend fun removeArtistPhoto(artistId: Long)

    /** Tracks with the given ids in the same order; ids of missing or unknown tracks are skipped. */
    suspend fun tracksByIds(ids: List<Long>): List<Track>

    /** Scans the device audio and updates the cache. Only new or changed files are read. */
    suspend fun scan()
}
