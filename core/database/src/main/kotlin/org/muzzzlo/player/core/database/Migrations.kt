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
