package org.muzzzlo.player.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.muzzzlo.player.core.database.entity.ListenEventEntity

/** Listen events of the statistics (DATABASE.md, `listen_event`). */
@Dao
interface ListenEventDao {
    @Insert
    suspend fun insert(event: ListenEventEntity): Long

    /** Every event, the latest first. */
    @Query("SELECT * FROM listen_event ORDER BY started_at DESC, id DESC")
    suspend fun all(): List<ListenEventEntity>

    /** Counted listens, skips and played time of `[from, to)`. */
    @Query(
        """
        SELECT COALESCE(SUM(counted), 0) AS plays,
               COALESCE(SUM(skipped), 0) AS skips,
               COALESCE(SUM(CASE WHEN counted = 1 THEN played_ms ELSE 0 END), 0) AS playedMs
        FROM listen_event
        WHERE started_at >= :from AND started_at < :to
        """,
    )
    fun observeTotals(
        from: Long,
        to: Long,
    ): Flow<TotalsRow>

    /**
     * Counted listens per track. A track that is gone (`track_id IS NULL`) is grouped by its snapshot, and the
     * title and artists come from the latest listen of the group (SQLite picks the row of `MAX`).
     */
    @Query(
        """
        SELECT e.track_id AS trackId,
               COALESCE(t.title, e.title_snapshot) AS title,
               e.artists_snapshot AS artistsSnapshot,
               COUNT(*) AS plays,
               SUM(e.played_ms) AS playedMs,
               MAX(e.started_at) AS lastAt
        FROM listen_event e LEFT JOIN track t ON t.id = e.track_id
        WHERE e.counted = 1 AND e.started_at >= :from AND e.started_at < :to
        GROUP BY COALESCE(CAST(e.track_id AS TEXT), e.title_snapshot || char(0) || e.artists_snapshot)
        ORDER BY plays DESC, playedMs DESC, title COLLATE NOCASE ASC, e.id ASC
        LIMIT :limit
        """,
    )
    fun observeTopTracks(
        from: Long,
        to: Long,
        limit: Int,
    ): Flow<List<TopTrackRow>>

    /** Counted listens per album of the tracks that are still in the database. */
    @Query(
        """
        SELECT a.id AS albumId, a.title AS title, COUNT(*) AS plays, SUM(e.played_ms) AS playedMs
        FROM listen_event e
        JOIN track t ON t.id = e.track_id
        JOIN album a ON a.id = t.album_id
        WHERE e.counted = 1 AND e.started_at >= :from AND e.started_at < :to
        GROUP BY a.id
        ORDER BY plays DESC, playedMs DESC, a.title COLLATE NOCASE ASC, a.id ASC
        LIMIT :limit
        """,
    )
    fun observeTopAlbums(
        from: Long,
        to: Long,
        limit: Int,
    ): Flow<List<TopAlbumRow>>

    /** Counted listens per artist: a listen goes to every artist of the track (`track_artist`). */
    @Query(
        """
        SELECT ar.id AS artistId, ar.name AS name, COUNT(*) AS plays, SUM(e.played_ms) AS playedMs
        FROM listen_event e
        JOIN track_artist ta ON ta.track_id = e.track_id
        JOIN artist ar ON ar.id = ta.artist_id
        WHERE e.counted = 1 AND e.started_at >= :from AND e.started_at < :to
        GROUP BY ar.id
        """,
    )
    fun observeArtistPlays(
        from: Long,
        to: Long,
    ): Flow<List<ArtistPlaysRow>>

    /**
     * Albums of the latest listens, counted or skipped, the most recent first. Listens of tracks that are gone and
     * tracks without an album are left out.
     */
    @Query(
        """
        SELECT t.album_id
        FROM listen_event e
        JOIN track t ON t.id = e.track_id
        WHERE t.album_id IS NOT NULL
        GROUP BY t.album_id
        ORDER BY MAX(e.started_at) DESC, MAX(e.id) DESC
        LIMIT :limit
        """,
    )
    fun observeRecentAlbumIds(limit: Int): Flow<List<Long>>

    /** Counted listens of tracks that are gone: only the snapshot of their artists is left. */
    @Query(
        """
        SELECT artists_snapshot AS artistsSnapshot, played_ms AS playedMs
        FROM listen_event
        WHERE counted = 1 AND track_id IS NULL AND started_at >= :from AND started_at < :to
        """,
    )
    fun observeOrphanArtistPlays(
        from: Long,
        to: Long,
    ): Flow<List<OrphanPlayRow>>
}

data class TotalsRow(
    val plays: Int,
    val skips: Int,
    val playedMs: Long,
)

data class TopTrackRow(
    val trackId: Long?,
    val title: String,
    val artistsSnapshot: String,
    val plays: Int,
    val playedMs: Long,
    /** Start of the latest listen of the group: the snapshot columns belong to it. */
    val lastAt: Long,
)

data class TopAlbumRow(
    val albumId: Long,
    val title: String,
    val plays: Int,
    val playedMs: Long,
)

data class ArtistPlaysRow(
    val artistId: Long,
    val name: String,
    val plays: Int,
    val playedMs: Long,
)

data class OrphanPlayRow(
    @ColumnInfo(name = "artistsSnapshot") val artistsSnapshot: String,
    @ColumnInfo(name = "playedMs") val playedMs: Long,
)
