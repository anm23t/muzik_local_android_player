package org.muzzzlo.player.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Every migration keeps the rows and ends in the schema Room exported for the new version. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MuzzzloDatabase::class.java)

    @Test
    fun `1 to 2 adds is_damaged and keeps tracks`() {
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL(
                "INSERT INTO track (id, media_store_id, uri, title, title_key, artist_tag_values, duration_ms, " +
                    "has_cover, has_lyrics, is_missing, is_favorite) " +
                    "VALUES (1, 10, 'content://media/10', 'Song', 'song', '[\"A\"]', 1000, 1, 0, 0, 1)",
            )
        }

        // Also validates the result against schemas/…/2.json.
        helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2).use { db ->
            db.query("SELECT title, has_cover, is_favorite, is_damaged FROM track WHERE id = 1").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Song", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(1, cursor.getInt(2))
                assertEquals(0, cursor.getInt(3))
            }
        }
    }

    @Test
    fun `2 to 3 clears date_modified so every file is read again`() {
        helper.createDatabase(DB_NAME, 2).use { db ->
            db.execSQL(
                "INSERT INTO track (id, media_store_id, uri, title, title_key, artist_tag_values, duration_ms, " +
                    "date_modified, has_cover, has_lyrics, is_missing, is_damaged, rating_tenths, is_favorite) " +
                    "VALUES (1, 10, 'content://media/10', 'Song', 'song', '[\"A\"]', 1000, 55, 1, 0, 0, 0, 67, 1)",
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 3, true, MIGRATION_2_3).use { db ->
            db.query("SELECT title, date_modified, rating_tenths, is_favorite FROM track WHERE id = 1").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Song", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertEquals(67, cursor.getInt(2))
                assertEquals(1, cursor.getInt(3))
            }
        }
    }

    @Test
    fun `3 to 4 adds artist_alias and keeps artists`() {
        helper.createDatabase(DB_NAME, 3).use { db ->
            db.execSQL("INSERT INTO artist (id, name, name_key) VALUES (1, 'Jay-Z', 'jay-z')")
        }

        helper.runMigrationsAndValidate(DB_NAME, 4, true, MIGRATION_3_4).use { db ->
            db.execSQL("INSERT INTO artist_alias (alias_key, alias_display, artist_id) VALUES ('jay z', 'Jay Z', 1)")
            db.query("SELECT a.name, l.alias_display FROM artist a JOIN artist_alias l ON l.artist_id = a.id").use {
                it.moveToFirst()
                assertEquals("Jay-Z", it.getString(0))
                assertEquals("Jay Z", it.getString(1))
            }
        }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
