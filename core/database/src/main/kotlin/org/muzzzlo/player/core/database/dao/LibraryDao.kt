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
import org.muzzzlo.player.core.database.entity.TrackScanFields

/**
 * Album row with the number of present tracks.
 *
 * @property dateAdded when the first of its tracks appeared on the device (MediaStore `date_added`, seconds).
 */
data class AlbumRow(
    val id: Long,
    val title: String,
    val year: Int?,
    @ColumnInfo(name = "track_count") val trackCount: Int,
    @ColumnInfo(name = "date_added") val dateAdded: Long?,
)

/**
 * Artist with the number of present tracks.
 *
 * @property photoPath copy of the photo the user picked (column `photo_uri`).
 * @property dateAdded when the first of their tracks appeared on the device (MediaStore `date_added`, seconds).
 */
data class ArtistRow(
    val id: Long,
    val name: String,
    @ColumnInfo(name = "photo_uri") val photoPath: String?,
    @ColumnInfo(name = "track_count") val trackCount: Int,
    @ColumnInfo(name = "date_added") val dateAdded: Long?,
)

/**
 * An album of an artist.
 *
 * @property isOwn the artist is one of the album artists; false when they only appear on some of its tracks.
 */
data class ArtistAlbumRef(
    @ColumnInfo(name = "album_id") val albumId: Long,
    @ColumnInfo(name = "is_own") val isOwn: Boolean,
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

/** Album id and title pair. */
data class AlbumTitle(val id: Long, val title: String)

/** Fields of a track used to decide whether a file must be rescanned. */
data class TrackStamp(
    val id: Long,
    val uri: String,
    @ColumnInfo(name = "date_modified") val dateModified: Long?,
    @ColumnInfo(name = "is_missing") val isMissing: Boolean,
    @ColumnInfo(name = "is_damaged") val isDamaged: Boolean,
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
        "SELECT a.id, a.title, a.year, COUNT(t.id) AS track_count, MIN(t.date_added) AS date_added FROM album a " +
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

    @Query(
        "SELECT ar.id, ar.name, ar.photo_uri, COUNT(t.id) AS track_count, MIN(t.date_added) AS date_added " +
            "FROM artist ar JOIN track_artist ta ON ta.artist_id = ar.id " +
            "JOIN track t ON t.id = ta.track_id AND t.is_missing = 0 " +
            "GROUP BY ar.id ORDER BY ar.name_key",
    )
    fun observeArtists(): Flow<List<ArtistRow>>

    @Query(
        "SELECT t.* FROM track t JOIN track_artist ta ON ta.track_id = t.id " +
            "WHERE ta.artist_id = :artistId AND t.is_missing = 0 ORDER BY t.title_key",
    )
    fun observeArtistTracks(artistId: Long): Flow<List<TrackEntity>>

    /** Albums credited to the artist and albums with at least one present track of theirs. */
    @Query(
        "SELECT a.id AS album_id, " +
            "EXISTS (SELECT 1 FROM album_artist aa WHERE aa.album_id = a.id AND aa.artist_id = :artistId) AS is_own " +
            "FROM album a WHERE a.id IN (SELECT album_id FROM album_artist WHERE artist_id = :artistId) " +
            "OR a.id IN (SELECT t.album_id FROM track t JOIN track_artist ta ON ta.track_id = t.id " +
            "WHERE ta.artist_id = :artistId AND t.is_missing = 0)",
    )
    fun observeArtistAlbumRefs(artistId: Long): Flow<List<ArtistAlbumRef>>

    @Query("SELECT photo_uri FROM artist WHERE id = :artistId")
    suspend fun artistPhotoPath(artistId: Long): String?

    /** Photos are user data: a rescan finds artists by name key and leaves this column alone. */
    @Query("UPDATE artist SET photo_uri = :path WHERE id = :artistId")
    suspend fun setArtistPhotoPath(
        artistId: Long,
        path: String?,
    )

    @Query("SELECT * FROM track WHERE is_missing = 0 AND id IN (:ids)")
    suspend fun tracksByIds(ids: List<Long>): List<TrackEntity>

    @Query(
        "SELECT ta.track_id, ar.name FROM track_artist ta JOIN artist ar ON ar.id = ta.artist_id " +
            "WHERE ta.track_id IN (:trackIds) ORDER BY ta.track_id, ta.position",
    )
    suspend fun artistNamesFor(trackIds: List<Long>): List<TrackArtistName>

    @Query("SELECT id, title FROM album WHERE id IN (:ids)")
    suspend fun albumTitles(ids: List<Long>): List<AlbumTitle>

    // ---- writes for the scanner ----

    @Query("SELECT id, uri, date_modified, is_missing, is_damaged FROM track")
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

    /** Updates only the scan-derived columns; rating and favorite are left as they are. */
    @Update(entity = TrackEntity::class)
    suspend fun updateScannedTrack(fields: TrackScanFields)

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
