package org.muzzzlo.player.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.muzzzlo.player.core.database.entity.AlbumArtistEntity
import org.muzzzlo.player.core.database.entity.AlbumEntity
import org.muzzzlo.player.core.database.entity.ArtistEntity
import org.muzzzlo.player.core.database.entity.TrackArtistEntity
import org.muzzzlo.player.core.database.entity.TrackEntity

/** Album row with the number of present tracks. */
data class AlbumRow(
    val id: Long,
    val title: String,
    val year: Int?,
    @ColumnInfo(name = "track_count") val trackCount: Int,
)

/** Track to artist name pair, already ordered by `position` in the query. */
data class TrackArtistName(
    @ColumnInfo(name = "track_id") val trackId: Long,
    val name: String,
)

/** Album to artist name pair, already ordered by `position` in the query. */
data class AlbumArtistName(
    @ColumnInfo(name = "album_id") val albumId: Long,
    val name: String,
)

/** Fields of a track used to decide whether a file must be rescanned. */
data class TrackStamp(
    val id: Long,
    val uri: String,
    @ColumnInfo(name = "date_modified") val dateModified: Long?,
    @ColumnInfo(name = "is_missing") val isMissing: Boolean,
)

@Dao
interface LibraryDao {
    // ---- reads for the UI ----

    @Query("SELECT t.* FROM track t LEFT JOIN album a ON a.id = t.album_id WHERE t.is_missing = 0 ORDER BY t.title_key")
    fun observeTracks(): Flow<List<TrackEntity>>

    @Query(
        "SELECT * FROM track WHERE is_missing = 0 AND album_id = :albumId " +
            "ORDER BY disc_number, track_number, title_key",
    )
    fun observeAlbumTracks(albumId: Long): Flow<List<TrackEntity>>

    @Query(
        "SELECT a.id, a.title, a.year, COUNT(t.id) AS track_count FROM album a " +
            "JOIN track t ON t.album_id = a.id AND t.is_missing = 0 " +
            "GROUP BY a.id ORDER BY a.title_key",
    )
    fun observeAlbums(): Flow<List<AlbumRow>>

    @Query(
        "SELECT ta.track_id, ar.name FROM track_artist ta JOIN artist ar ON ar.id = ta.artist_id " +
            "ORDER BY ta.track_id, ta.position",
    )
    fun observeTrackArtists(): Flow<List<TrackArtistName>>

    @Query(
        "SELECT aa.album_id, ar.name FROM album_artist aa JOIN artist ar ON ar.id = aa.artist_id " +
            "ORDER BY aa.album_id, aa.position",
    )
    fun observeAlbumArtists(): Flow<List<AlbumArtistName>>

    // ---- writes for the scanner ----

    @Query("SELECT id, uri, date_modified, is_missing FROM track")
    suspend fun trackStamps(): List<TrackStamp>

    @Query("SELECT id FROM artist WHERE name_key = :key")
    suspend fun artistIdByKey(key: String): Long?

    @Query("SELECT id FROM album WHERE title_key = :key AND IFNULL(year, 0) = IFNULL(:year, 0)")
    suspend fun albumIdsByKey(
        key: String,
        year: Int?,
    ): List<Long>

    @Query("SELECT artist_id FROM album_artist WHERE album_id = :albumId ORDER BY position")
    suspend fun albumArtistIds(albumId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArtist(artist: ArtistEntity): Long

    @Insert
    suspend fun insertAlbum(album: AlbumEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbumArtists(rows: List<AlbumArtistEntity>)

    @Insert
    suspend fun insertTrack(track: TrackEntity): Long

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Query("DELETE FROM track_artist WHERE track_id = :trackId")
    suspend fun clearTrackArtists(trackId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackArtists(rows: List<TrackArtistEntity>)

    @Query("UPDATE track SET is_missing = :missing WHERE id IN (:ids)")
    suspend fun setMissing(
        ids: List<Long>,
        missing: Boolean,
    )

    @Query("DELETE FROM album WHERE id NOT IN (SELECT DISTINCT album_id FROM track WHERE album_id IS NOT NULL)")
    suspend fun deleteEmptyAlbums()

    /** Finds or creates an artist by its comparison key. */
    @Transaction
    suspend fun artistId(
        name: String,
        key: String,
    ): Long {
        artistIdByKey(key)?.let { return it }
        insertArtist(ArtistEntity(name = name, nameKey = key))
        return checkNotNull(artistIdByKey(key))
    }
}
