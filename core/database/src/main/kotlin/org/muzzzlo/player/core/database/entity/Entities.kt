package org.muzzzlo.player.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "artist", indices = [Index(value = ["name_key"], unique = true)])
data class ArtistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "name_key") val nameKey: String,
    @ColumnInfo(name = "sort_name") val sortName: String? = null,
    @ColumnInfo(name = "photo_uri") val photoUri: String? = null,
)

@Entity(tableName = "album", indices = [Index("title_key")])
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "title_key") val titleKey: String,
    val year: Int? = null,
    @ColumnInfo(name = "cover_hash") val coverHash: String? = null,
    @ColumnInfo(name = "accent_color") val accentColor: Int? = null,
)

@Entity(
    tableName = "album_artist",
    primaryKeys = ["album_id", "artist_id"],
    foreignKeys = [
        ForeignKey(AlbumEntity::class, ["id"], ["album_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ArtistEntity::class, ["id"], ["artist_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("artist_id")],
)
data class AlbumArtistEntity(
    @ColumnInfo(name = "album_id") val albumId: Long,
    @ColumnInfo(name = "artist_id") val artistId: Long,
    val position: Int,
)

@Entity(
    tableName = "track",
    foreignKeys = [
        ForeignKey(AlbumEntity::class, ["id"], ["album_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index(value = ["uri"], unique = true),
        Index("album_id"),
        Index("title_key"),
        Index("date_modified"),
    ],
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long?,
    val uri: String,
    @ColumnInfo(name = "relative_path") val relativePath: String? = null,
    val title: String,
    @ColumnInfo(name = "title_key") val titleKey: String,
    /** Artist tag values as read from the file (JSON array), lets the split be recomputed without reading files. */
    @ColumnInfo(name = "artist_tag_values") val artistTagValues: String,
    @ColumnInfo(name = "album_id") val albumId: Long? = null,
    @ColumnInfo(name = "track_number") val trackNumber: Int? = null,
    @ColumnInfo(name = "disc_number") val discNumber: Int? = null,
    val year: Int? = null,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    val format: String? = null,
    val bitrate: Int? = null,
    @ColumnInfo(name = "file_size") val fileSize: Long? = null,
    @ColumnInfo(name = "date_added") val dateAdded: Long? = null,
    @ColumnInfo(name = "date_modified") val dateModified: Long? = null,
    @ColumnInfo(name = "has_cover") val hasCover: Boolean = false,
    @ColumnInfo(name = "has_lyrics") val hasLyrics: Boolean = false,
    @ColumnInfo(name = "is_missing", defaultValue = "0") val isMissing: Boolean = false,
    /** The file crashed the native tag reader and is quarantined; the row holds MediaStore data only. */
    @ColumnInfo(name = "is_damaged", defaultValue = "0") val isDamaged: Boolean = false,
    /** Rating 0-100 (tenths of a point, 67 = 6.7); null when not rated. Stored only in the database. */
    @ColumnInfo(name = "rating_tenths") val ratingTenths: Int? = null,
    @ColumnInfo(name = "is_favorite", defaultValue = "0") val isFavorite: Boolean = false,
)

@Entity(
    tableName = "track_artist",
    primaryKeys = ["track_id", "artist_id"],
    foreignKeys = [
        ForeignKey(TrackEntity::class, ["id"], ["track_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ArtistEntity::class, ["id"], ["artist_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("artist_id")],
)
data class TrackArtistEntity(
    @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "artist_id") val artistId: Long,
    val position: Int,
)
