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
