package org.muzzzlo.player.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import org.muzzzlo.player.core.database.entity.ListenEventEntity

/** Listen events of the statistics (DATABASE.md, `listen_event`). */
@Dao
interface ListenEventDao {
    @Insert
    suspend fun insert(event: ListenEventEntity): Long

    /** Every event, the latest first. */
    @Query("SELECT * FROM listen_event ORDER BY started_at DESC, id DESC")
    suspend fun all(): List<ListenEventEntity>
}
