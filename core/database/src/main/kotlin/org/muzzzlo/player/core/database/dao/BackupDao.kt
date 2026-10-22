package org.muzzzlo.player.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import org.muzzzlo.player.core.database.entity.PlaylistEntity

/** What a track needs to be found again after a restore: title, artist tags (JSON array) and length. */
data class TrackIdentity(
    val id: Long,
    val title: String,
    @ColumnInfo(name = "artist_tag_values") val artistTagValues: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "rating_tenths") val ratingTenths: Int?,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
)

/** An artist spelling with the name of its artist. */
data class AliasWithArtist(
    @ColumnInfo(name = "alias_key") val aliasKey: String,
    @ColumnInfo(name = "alias_display") val aliasDisplay: String,
    @ColumnInfo(name = "artist_name") val artistName: String,
)

/** A playlist entry with what its track needs to be found again; in playlist order. */
data class PlaylistEntryIdentity(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    val title: String,
    @ColumnInfo(name = "artist_tag_values") val artistTagValues: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
)

/** Reads and writes the data that the backup carries and the other DAOs do not expose (ARCHITECTURE.md, section 8). */
@Dao
interface BackupDao {
    @Query("SELECT id, title, artist_tag_values, duration_ms, rating_tenths, is_favorite FROM track")
    suspend fun trackIdentities(): List<TrackIdentity>

    @Query(
        """
        SELECT a.alias_key, a.alias_display, ar.name AS artist_name
        FROM artist_alias a JOIN artist ar ON ar.id = a.artist_id
        ORDER BY a.alias_key
        """,
    )
    suspend fun aliases(): List<AliasWithArtist>

    @Query("UPDATE track SET rating_tenths = :rating, is_favorite = :favorite WHERE id = :id")
    suspend fun setMark(
        id: Long,
        rating: Int?,
        favorite: Boolean,
    )

    @Query("SELECT * FROM playlist ORDER BY id")
    suspend fun playlists(): List<PlaylistEntity>

    /** Entries of every playlist, missing files included: they are the user's choice and may come back. */
    @Query(
        """
        SELECT pt.playlist_id, t.title, t.artist_tag_values, t.duration_ms
        FROM playlist_track pt JOIN track t ON t.id = pt.track_id
        ORDER BY pt.playlist_id, pt.position, pt.id
        """,
    )
    suspend fun playlistEntries(): List<PlaylistEntryIdentity>

    @Query("UPDATE listen_event SET track_id = :trackId WHERE id = :eventId")
    suspend fun linkEvent(
        eventId: Long,
        trackId: Long,
    )
}
