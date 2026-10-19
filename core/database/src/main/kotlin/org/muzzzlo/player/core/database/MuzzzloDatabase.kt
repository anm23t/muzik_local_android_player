package org.muzzzlo.player.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import org.muzzzlo.player.core.database.dao.LibraryDao
import org.muzzzlo.player.core.database.entity.AlbumArtistEntity
import org.muzzzlo.player.core.database.entity.AlbumEntity
import org.muzzzlo.player.core.database.entity.ArtistAliasEntity
import org.muzzzlo.player.core.database.entity.ArtistCompoundEntity
import org.muzzzlo.player.core.database.entity.ArtistEntity
import org.muzzzlo.player.core.database.entity.TrackArtistEntity
import org.muzzzlo.player.core.database.entity.TrackEntity

/** Library cache. Schema is described in DATABASE.md. */
@Database(
    entities = [
        ArtistEntity::class,
        ArtistAliasEntity::class,
        ArtistCompoundEntity::class,
        AlbumEntity::class,
        AlbumArtistEntity::class,
        TrackEntity::class,
        TrackArtistEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class MuzzzloDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        const val NAME = "muzzzlo.db"
    }
}
