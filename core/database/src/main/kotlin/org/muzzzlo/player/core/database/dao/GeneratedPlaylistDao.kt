package org.muzzzlo.player.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.muzzzlo.player.core.database.entity.GeneratedPlaylistEntity
import org.muzzzlo.player.core.database.entity.GeneratedPlaylistTrackEntity
import org.muzzzlo.player.core.database.entity.TrackEntity

/** A generated set with the number of its present tracks and its latest listen. */
data class GeneratedPlaylistRow(
    val id: Long,
    val kind: String,
    val day: Long,
    val gradient: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "track_count") val trackCount: Int,
    @ColumnInfo(name = "last_started_at") val lastStartedAt: Long?,
    val period: String? = null,
)

private const val GENERATED_ROWS =
    "SELECT g.id, g.kind, g.day, g.gradient, g.created_at, g.period, " +
        "(SELECT COUNT(*) FROM generated_playlist_track gt JOIN track t ON t.id = gt.track_id AND t.is_missing = 0 " +
        "WHERE gt.generated_id = g.id) AS track_count, " +
        "(SELECT MAX(e.started_at) FROM listen_event e " +
        "WHERE e.source_kind = g.kind AND e.started_at >= g.created_at) AS last_started_at " +
        "FROM generated_playlist g"

/** Generated playlists of the home screen (DATABASE.md, `generated_playlist`, task 2.5.7). */
@Dao
interface GeneratedPlaylistDao {
    @Query(GENERATED_ROWS)
    fun observeAll(): Flow<List<GeneratedPlaylistRow>>

    @Query("$GENERATED_ROWS WHERE g.kind = :kind")
    fun observe(kind: String): Flow<GeneratedPlaylistRow?>

    @Query("SELECT * FROM generated_playlist")
    suspend fun all(): List<GeneratedPlaylistEntity>

    @Query("SELECT * FROM generated_playlist WHERE kind = :kind")
    suspend fun byKind(kind: String): GeneratedPlaylistEntity?

    /** Present tracks of the set of [kind] in order. */
    @Query(
        "SELECT t.* FROM generated_playlist_track gt JOIN generated_playlist g ON g.id = gt.generated_id " +
            "JOIN track t ON t.id = gt.track_id AND t.is_missing = 0 WHERE g.kind = :kind ORDER BY gt.position",
    )
    fun observeTracks(kind: String): Flow<List<TrackEntity>>

    @Query(
        "SELECT gt.track_id FROM generated_playlist_track gt JOIN generated_playlist g ON g.id = gt.generated_id " +
            "JOIN track t ON t.id = gt.track_id AND t.is_missing = 0 WHERE g.kind = :kind ORDER BY gt.position",
    )
    suspend fun trackIds(kind: String): List<Long>

    @Query("DELETE FROM generated_playlist WHERE kind = :kind")
    suspend fun deleteKind(kind: String)

    @Insert
    suspend fun insert(playlist: GeneratedPlaylistEntity): Long

    @Insert
    suspend fun insertTracks(tracks: List<GeneratedPlaylistTrackEntity>)

    /** Puts a new set of [kind] in place of the old one; its tracks go with it. */
    @Transaction
    suspend fun replace(
        playlist: GeneratedPlaylistEntity,
        trackIds: List<Long>,
    ) {
        deleteKind(playlist.kind)
        val id = insert(playlist)
        insertTracks(trackIds.mapIndexed { position, trackId -> GeneratedPlaylistTrackEntity(id, trackId, position) })
    }
}
