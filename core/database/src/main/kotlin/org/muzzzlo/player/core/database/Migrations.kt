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
