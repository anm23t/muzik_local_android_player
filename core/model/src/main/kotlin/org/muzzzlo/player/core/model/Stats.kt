package org.muzzzlo.player.core.model

import kotlinx.coroutines.flow.Flow

/**
 * A track in a top. [trackId] is null for a track that is gone from the library: such listens are grouped by the
 * snapshot of the title and the artists (DATABASE.md, `listen_event`).
 *
 * @property plays counted listens in the period.
 * @property playedMs time played over those listens.
 */
data class TopTrack(
    val trackId: Long?,
    val title: String,
    val artists: List<String>,
    val plays: Int,
    val playedMs: Long,
)

/** An album in a top: every counted listen of its tracks. Listens of deleted tracks have no album and do not count. */
data class TopAlbum(
    val albumId: Long,
    val title: String,
    val plays: Int,
    val playedMs: Long,
)

/**
 * An artist in a top. Every artist of a track gets the listen, so "A; B" counts for A and for B and never as a
 * separate "A; B" row. [artistId] is null when the artist exists only in snapshots of deleted tracks.
 */
data class TopArtist(
    val artistId: Long?,
    val name: String,
    val plays: Int,
    val playedMs: Long,
)

/** Totals of a period. A skipped listen is not counted ([ListenEvent.skipped]), so the two numbers do not overlap. */
data class StatsTotals(
    val plays: Int,
    val skips: Int,
    val playedMs: Long,
)

/** Statistics queries over the listen history. Every Flow emits again when new listens are recorded. */
interface StatsRepository {
    fun totals(period: StatsPeriod): Flow<StatsTotals>

    fun topTracks(
        period: StatsPeriod,
        limit: Int = DEFAULT_TOP_LIMIT,
    ): Flow<List<TopTrack>>

    fun topAlbums(
        period: StatsPeriod,
        limit: Int = DEFAULT_TOP_LIMIT,
    ): Flow<List<TopAlbum>>

    fun topArtists(
        period: StatsPeriod,
        limit: Int = DEFAULT_TOP_LIMIT,
    ): Flow<List<TopArtist>>

    /** The most listened tracks of one artist, all time. Tracks that are gone from the library are left out. */
    fun artistTopTracks(
        artistId: Long,
        limit: Int = DEFAULT_TOP_LIMIT,
    ): Flow<List<TopTrack>>

    /** Counted listens of all tracks of one artist, all time; every artist of a track gets the listen. */
    fun artistPlays(artistId: Long): Flow<Int>

    /**
     * Ids of the albums of the latest listens (counted or skipped), the most recent first, at most [limit].
     * Listens of deleted tracks and of tracks without an album do not count.
     */
    fun recentAlbumIds(limit: Int = DEFAULT_TOP_LIMIT): Flow<List<Long>>

    companion object {
        const val DEFAULT_TOP_LIMIT = 5
    }
}
