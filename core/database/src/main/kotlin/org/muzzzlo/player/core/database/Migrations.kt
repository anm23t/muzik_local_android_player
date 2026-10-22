package org.muzzzlo.player.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Version 2: `track.is_damaged`, set for files that crashed the native tag reader (task 0.3.2). */
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE track ADD COLUMN is_damaged INTEGER NOT NULL DEFAULT 0")
        }
    }

/**
 * Version 3: no schema change. Album artists now come from the album artist tag (task 0.3.5a), so every file is
 * read again on the next scan: clearing `date_modified` makes the indexer treat each track as changed.
 */
val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE track SET date_modified = NULL")
        }
    }

/** Version 4: `artist_alias`, spellings that point to one artist (task 0.4.4, the "Check" button fills it in 0.4.5). */
val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `artist_alias` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`alias_key` TEXT NOT NULL, `alias_display` TEXT NOT NULL, `artist_id` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`artist_id`) REFERENCES `artist`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_artist_alias_alias_key` ON `artist_alias` (`alias_key`)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_artist_alias_artist_id` ON `artist_alias` (`artist_id`)")
        }
    }

/** Version 5: `artist_compound`, artist names with a comma inside (task 0.4.6). */
val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `artist_compound` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`display_name` TEXT NOT NULL, `match_key` TEXT NOT NULL, `builtin_id` TEXT, " +
                    "`enabled` INTEGER NOT NULL DEFAULT 1, `created_at` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_artist_compound_match_key` " +
                    "ON `artist_compound` (`match_key`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_artist_compound_builtin_id` " +
                    "ON `artist_compound` (`builtin_id`)",
            )
        }
    }

/**
 * Version 6: `track.track_total` and `track.disc_total` for the artist card sections (task 0.4.11). Clearing
 * `date_modified` makes the next scan read every file again, so the new columns get filled.
 */
val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE track ADD COLUMN track_total INTEGER")
            db.execSQL("ALTER TABLE track ADD COLUMN disc_total INTEGER")
            db.execSQL("UPDATE track SET date_modified = NULL")
        }
    }

/**
 * Version 7: ReplayGain values of a track (task 0.5.3). Clearing `date_modified` makes the next scan read every file
 * again, so the new columns get filled.
 */
val MIGRATION_6_7 =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE track ADD COLUMN rg_track_gain REAL")
            db.execSQL("ALTER TABLE track ADD COLUMN rg_track_peak REAL")
            db.execSQL("ALTER TABLE track ADD COLUMN rg_album_gain REAL")
            db.execSQL("ALTER TABLE track ADD COLUMN rg_album_peak REAL")
            db.execSQL("UPDATE track SET date_modified = NULL")
        }
    }

/** Version 8: `listen_event`, the listens of the statistics (task 0.6.1). */
val MIGRATION_7_8 =
    object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `listen_event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`track_id` INTEGER, `started_at` INTEGER NOT NULL, `played_ms` INTEGER NOT NULL, " +
                    "`duration_ms` INTEGER NOT NULL, `counted` INTEGER NOT NULL, `skipped` INTEGER NOT NULL, " +
                    "`title_snapshot` TEXT NOT NULL, `artists_snapshot` TEXT NOT NULL, " +
                    "FOREIGN KEY(`track_id`) REFERENCES `track`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_listen_event_started_at` ON `listen_event` (`started_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_listen_event_track_id` ON `listen_event` (`track_id`)")
        }
    }

/**
 * Version 9: `playlist` and `playlist_track`, and `listen_event.playlist_id`, the playlist a listen was started from
 * (task 1.1.5).
 */
val MIGRATION_8_9 =
    object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `playlist` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `last_played_at` INTEGER)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `playlist_track` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`playlist_id` INTEGER NOT NULL, `track_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`playlist_id`) REFERENCES `playlist`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`track_id`) REFERENCES `track`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_playlist_track_playlist_id_position` " +
                    "ON `playlist_track` (`playlist_id`, `position`)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_track_track_id` ON `playlist_track` (`track_id`)")
            db.execSQL(
                "ALTER TABLE `listen_event` ADD COLUMN `playlist_id` INTEGER " +
                    "REFERENCES `playlist`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_listen_event_playlist_id` ON `listen_event` (`playlist_id`)")
        }
    }

/**
 * Version 10: `track.favorited_at`, the moment a track became a favorite, for the "Favorites" order (task 1.1.6).
 * Favorites that exist already (from a backup) get 0, so they come after the new ones.
 */
val MIGRATION_9_10 =
    object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `track` ADD COLUMN `favorited_at` INTEGER")
            db.execSQL("UPDATE `track` SET `favorited_at` = 0 WHERE `is_favorite` = 1")
        }
    }
