package org.muzzzlo.player.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.muzzzlo.player.core.database.entity.PlaylistEntity
import org.muzzzlo.player.core.database.entity.PlaylistTrackEntity
import org.muzzzlo.player.core.database.entity.TrackEntity

private const val PLAYLIST_ROWS =
    "SELECT p.id, p.name, p.created_at, p.last_played_at, " +
        "(SELECT COUNT(*) FROM playlist_track pt JOIN track t ON t.id = pt.track_id AND t.is_missing = 0 " +
        "WHERE pt.playlist_id = p.id) AS track_count, " +
        "(SELECT pt.track_id FROM playlist_track pt JOIN track t ON t.id = pt.track_id AND t.is_missing = 0 " +
        "WHERE pt.playlist_id = p.id ORDER BY pt.position, pt.id LIMIT 1) AS first_track_id " +
        "FROM playlist p"

/** A playlist with what its tile needs; counts and the first track skip missing files. */
data class PlaylistRow(
    val id: Long,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long?,
    @ColumnInfo(name = "track_count") val trackCount: Int,
    @ColumnInfo(name = "first_track_id") val firstTrackId: Long?,
)

/** A visible entry of a playlist with its track. */
data class PlaylistEntryTrack(
    @ColumnInfo(name = "entry_id") val entryId: Long,
    @Embedded val track: TrackEntity,
)

/** An entry in playlist order; [visible] is false while its file is missing. */
data class PlaylistEntrySlot(
    val id: Long,
    val visible: Boolean,
)

/** Playlists and their tracks (DATABASE.md, `playlist` and `playlist_track`). */
@Dao
interface PlaylistDao {
    @Query("$PLAYLIST_ROWS ORDER BY p.name COLLATE NOCASE, p.id")
    fun observePlaylists(): Flow<List<PlaylistRow>>

    @Query("$PLAYLIST_ROWS WHERE p.id = :playlistId")
    fun observePlaylist(playlistId: Long): Flow<PlaylistRow?>

    @Query(
        "SELECT pt.id AS entry_id, t.* FROM playlist_track pt " +
            "JOIN track t ON t.id = pt.track_id AND t.is_missing = 0 " +
            "WHERE pt.playlist_id = :playlistId ORDER BY pt.position, pt.id",
    )
    fun observeEntries(playlistId: Long): Flow<List<PlaylistEntryTrack>>

    @Query(
        "SELECT pt.id AS id, t.is_missing = 0 AS visible FROM playlist_track pt " +
            "JOIN track t ON t.id = pt.track_id WHERE pt.playlist_id = :playlistId ORDER BY pt.position, pt.id",
    )
    suspend fun slots(playlistId: Long): List<PlaylistEntrySlot>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlist SET name = :name WHERE id = :playlistId")
    suspend fun rename(
        playlistId: Long,
        name: String,
    )

    @Query("DELETE FROM playlist WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("UPDATE playlist SET last_played_at = :at WHERE id = :playlistId")
    suspend fun setLastPlayed(
        playlistId: Long,
        at: Long,
    )

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM playlist_track WHERE playlist_id = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Insert
    suspend fun insertEntries(entries: List<PlaylistTrackEntity>)

    /** Appends the tracks to the end of the playlist in the given order. */
    @Transaction
    suspend fun append(
        playlistId: Long,
        trackIds: List<Long>,
    ) {
        val first = nextPosition(playlistId)
        insertEntries(
            trackIds.mapIndexed { i, id ->
                PlaylistTrackEntity(playlistId = playlistId, trackId = id, position = first + i)
            },
        )
    }

    @Query("DELETE FROM playlist_track WHERE id = :entryId")
    suspend fun deleteEntry(entryId: Long)

    @Query("UPDATE playlist_track SET position = :position WHERE id = :entryId")
    suspend fun setPosition(
        entryId: Long,
        position: Int,
    )

    /** Numbers the entries 0, 1, 2… in the order of [entryIds]. */
    @Transaction
    suspend fun setOrder(entryIds: List<Long>) {
        entryIds.forEachIndexed { position, id -> setPosition(id, position) }
    }
}
