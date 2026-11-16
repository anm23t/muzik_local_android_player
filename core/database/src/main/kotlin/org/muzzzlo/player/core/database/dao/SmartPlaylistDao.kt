package org.muzzzlo.player.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.muzzzlo.player.core.database.entity.SmartPlaylistEntity

/** Smart playlists (DATABASE.md, `smart_playlist`, task 2.5.6). */
@Dao
interface SmartPlaylistDao {
    @Query("SELECT * FROM smart_playlist ORDER BY created_at, id")
    fun observeAll(): Flow<List<SmartPlaylistEntity>>

    @Query("SELECT * FROM smart_playlist ORDER BY created_at, id")
    suspend fun all(): List<SmartPlaylistEntity>

    @Query("SELECT * FROM smart_playlist WHERE id = :id")
    suspend fun byId(id: Long): SmartPlaylistEntity?

    @Insert
    suspend fun insert(playlist: SmartPlaylistEntity): Long

    @Query("UPDATE smart_playlist SET name = :name, rules_json = :rulesJson WHERE id = :id")
    suspend fun update(
        id: Long,
        name: String,
        rulesJson: String,
    )

    @Query("DELETE FROM smart_playlist WHERE id = :id")
    suspend fun delete(id: Long)
}
