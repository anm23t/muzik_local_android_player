package org.muzzzlo.player.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import org.muzzzlo.player.core.database.dao.BackupDao
import org.muzzzlo.player.core.database.dao.LibraryDao
import org.muzzzlo.player.core.database.dao.ListenEventDao
import org.muzzzlo.player.core.database.dao.PlaylistDao
import org.muzzzlo.player.core.database.entity.AlbumArtistEntity
import org.muzzzlo.player.core.database.entity.AlbumEntity
import org.muzzzlo.player.core.database.entity.ArtistAliasEntity
import org.muzzzlo.player.core.database.entity.ArtistCompoundEntity
import org.muzzzlo.player.core.database.entity.ArtistEntity
import org.muzzzlo.player.core.database.entity.ListenEventEntity
import org.muzzzlo.player.core.database.entity.PlaylistEntity
import org.muzzzlo.player.core.database.entity.PlaylistTrackEntity
import org.muzzzlo.player.core.database.entity.TrackArtistEntity
import org.muzzzlo.player.core.database.entity.TrackEntity

/** Library cache, the listen events of the statistics and the playlists. Schema is described in DATABASE.md. */
@Database(
    entities = [
        ArtistEntity::class,
        ArtistAliasEntity::class,
        ArtistCompoundEntity::class,
        AlbumEntity::class,
        AlbumArtistEntity::class,
        TrackEntity::class,
        TrackArtistEntity::class,
        ListenEventEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
abstract class MuzzzloDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    abstract fun listenEventDao(): ListenEventDao

    abstract fun backupDao(): BackupDao

    abstract fun playlistDao(): PlaylistDao

    companion object {
        const val NAME = "muzzzlo.db"
    }
}
