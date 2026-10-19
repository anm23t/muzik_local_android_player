package org.muzzzlo.player.core.model

/** An alias taken away before a profile edit, kept to put it back when the edit fails. */
data class RemovedAlias(
    val key: String,
    val display: String,
    val artistId: Long,
)

/**
 * A profile edit in progress: what [ArtistProfileRepository.begin] changed in the database and what
 * [ArtistProfileRepository.commit] or [ArtistProfileRepository.rollback] needs to finish it.
 */
data class ArtistEditToken(
    val artistId: Long,
    val edit: ArtistEdit,
    val removedAliases: List<RemovedAlias>,
)

/**
 * Database side of artist profile changes (rename, merge, split). The tags themselves are written into the files
 * by the caller through [TrackTagRepository]; files are the source of truth, the database only follows
 * (CLAUDE.md, decision 1). Implemented in `core:library`.
 */
interface ArtistProfileRepository {
    /** Name of the artist, null when there is none. */
    suspend fun artistName(artistId: Long): String?

    /** Present tracks whose files mention the artist: as a track artist or through the album artists. */
    suspend fun affectedTrackIds(artistId: Long): List<Long>

    /**
     * Prepares the database before the files are written: aliases that would send a new name back to another
     * artist (for example an old spelling after a rename back) are dropped, otherwise reading the new tags
     * would credit the wrong artist.
     */
    suspend fun begin(
        artistId: Long,
        edit: ArtistEdit,
    ): ArtistEditToken

    /**
     * Finishes the edit after every file was written and read again. A rename or merge leaves the old spelling
     * as an alias of the new artist, moves the aliases and the photo (when the new artist has none) and
     * removes the old artist once nothing credits them. A split leaves no alias.
     */
    suspend fun commit(token: ArtistEditToken)

    /** Puts back what [begin] took away after the files were restored to their old tags. */
    suspend fun rollback(token: ArtistEditToken)
}
