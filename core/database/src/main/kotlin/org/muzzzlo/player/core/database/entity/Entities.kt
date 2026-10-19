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

/** Another spelling of an artist: tags with this key go to [artistId] (DATABASE.md). */
@Entity(
    tableName = "artist_alias",
    foreignKeys = [ForeignKey(ArtistEntity::class, ["id"], ["artist_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["alias_key"], unique = true), Index("artist_id")],
)
data class ArtistAliasEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "alias_key") val aliasKey: String,
    @ColumnInfo(name = "alias_display") val aliasDisplay: String,
    @ColumnInfo(name = "artist_id") val artistId: Long,
)

/**
 * An artist name with a comma inside, such as `Tyler, The Creator` (ARCHITECTURE.md, section 4).
 *
 * @property matchKey normalised fragments in order joined by `|`, for example `tyler|the creator`.
 * @property builtinId stable id of an entry of the built-in list, null for rules of the user.
 * @property enabled a built-in rule the user switched off stays here with 0 so an update does not bring it back.
 */
@Entity(
    tableName = "artist_compound",
    indices = [Index(value = ["match_key"], unique = true), Index(value = ["builtin_id"], unique = true)],
)
data class ArtistCompoundEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "match_key") val matchKey: String,
    @ColumnInfo(name = "builtin_id") val builtinId: String? = null,
    @ColumnInfo(defaultValue = "1") val enabled: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long,
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
    /** Number of tracks on the disc from the tags (schema 6), decides the "Singles" section. */
    @ColumnInfo(name = "track_total") val trackTotal: Int? = null,
    /** Number of discs from the tags (schema 6). */
    @ColumnInfo(name = "disc_total") val discTotal: Int? = null,
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

/**
 * The columns of [TrackEntity] that the scanner derives from the file and MediaStore. A rescan updates only
 * these, so data that lives only in the database (rating, favorite) survives a file change.
 */
data class TrackScanFields(
    val id: Long = 0,
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long?,
    val uri: String,
    @ColumnInfo(name = "relative_path") val relativePath: String?,
    val title: String,
    @ColumnInfo(name = "title_key") val titleKey: String,
    @ColumnInfo(name = "artist_tag_values") val artistTagValues: String,
    @ColumnInfo(name = "album_id") val albumId: Long?,
    @ColumnInfo(name = "track_number") val trackNumber: Int?,
    @ColumnInfo(name = "disc_number") val discNumber: Int?,
    @ColumnInfo(name = "track_total") val trackTotal: Int?,
    @ColumnInfo(name = "disc_total") val discTotal: Int?,
    val year: Int?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    val format: String?,
    val bitrate: Int?,
    @ColumnInfo(name = "file_size") val fileSize: Long?,
    @ColumnInfo(name = "date_added") val dateAdded: Long?,
    @ColumnInfo(name = "date_modified") val dateModified: Long?,
    @ColumnInfo(name = "has_cover") val hasCover: Boolean,
    @ColumnInfo(name = "has_lyrics") val hasLyrics: Boolean,
    /** A scanned file is present, so an update also brings a reappeared track back. */
    @ColumnInfo(name = "is_missing") val isMissing: Boolean = false,
    @ColumnInfo(name = "is_damaged") val isDamaged: Boolean,
) {
    /** A new track row with these fields and default user data. */
    fun toNewEntity() =
        TrackEntity(
            mediaStoreId = mediaStoreId,
            uri = uri,
            relativePath = relativePath,
            title = title,
            titleKey = titleKey,
            artistTagValues = artistTagValues,
            albumId = albumId,
            trackNumber = trackNumber,
            discNumber = discNumber,
            trackTotal = trackTotal,
            discTotal = discTotal,
            year = year,
            durationMs = durationMs,
            format = format,
            bitrate = bitrate,
            fileSize = fileSize,
            dateAdded = dateAdded,
            dateModified = dateModified,
            hasCover = hasCover,
            hasLyrics = hasLyrics,
            isMissing = isMissing,
            isDamaged = isDamaged,
        )
}

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
