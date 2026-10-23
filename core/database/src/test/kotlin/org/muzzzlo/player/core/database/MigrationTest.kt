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

    @Test
    fun `4 to 5 adds artist_compound and keeps aliases`() {
        helper.createDatabase(DB_NAME, 4).use { db ->
            db.execSQL("INSERT INTO artist (id, name, name_key) VALUES (1, 'Jay-Z', 'jay-z')")
            db.execSQL("INSERT INTO artist_alias (alias_key, alias_display, artist_id) VALUES ('jay z', 'Jay Z', 1)")
        }

        helper.runMigrationsAndValidate(DB_NAME, 5, true, MIGRATION_4_5).use { db ->
            db.execSQL(
                "INSERT INTO artist_compound (display_name, match_key, builtin_id, created_at) " +
                    "VALUES ('Tyler, The Creator', 'tyler|the creator', 'tyler-the-creator', 1)",
            )
            db.query("SELECT enabled FROM artist_compound").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
            }
            db.query("SELECT alias_display FROM artist_alias").use {
                it.moveToFirst()
                assertEquals("Jay Z", it.getString(0))
            }
        }
    }

    @Test
    fun `5 to 6 adds track and disc totals and clears date_modified`() {
        helper.createDatabase(DB_NAME, 5).use { db ->
            db.execSQL(
                "INSERT INTO track (id, media_store_id, uri, title, title_key, artist_tag_values, duration_ms, " +
                    "date_modified, has_cover, has_lyrics, is_missing, is_damaged, rating_tenths, is_favorite) " +
                    "VALUES (1, 10, 'content://media/10', 'Song', 'song', '[\"A\"]', 1000, 500, " +
                    "0, 0, 0, 0, 67, 1)",
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 6, true, MIGRATION_5_6).use { db ->
            db.query("SELECT title, track_total, disc_total, date_modified, rating_tenths, is_favorite FROM track")
                .use { cursor ->
                    cursor.moveToFirst()
                    assertEquals("Song", cursor.getString(0))
                    assertTrue(cursor.isNull(1))
                    assertTrue(cursor.isNull(2))
                    assertTrue(cursor.isNull(3))
                    assertEquals(67, cursor.getInt(4))
                    assertEquals(1, cursor.getInt(5))
                }
        }
    }

    @Test
    fun `6 to 7 adds ReplayGain columns and clears date_modified`() {
        helper.createDatabase(DB_NAME, 6).use { db ->
            db.execSQL(
                "INSERT INTO track (id, media_store_id, uri, title, title_key, artist_tag_values, duration_ms, " +
                    "track_total, date_modified, has_cover, has_lyrics, is_missing, is_damaged, rating_tenths, " +
                    "is_favorite) VALUES (1, 10, 'content://media/10', 'Song', 'song', '[\"A\"]', 1000, 12, 500, " +
                    "0, 0, 0, 0, 67, 1)",
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 7, true, MIGRATION_6_7).use { db ->
            db.query(
                "SELECT title, track_total, date_modified, rating_tenths, is_favorite, " +
                    "rg_track_gain, rg_track_peak, rg_album_gain, rg_album_peak FROM track",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("Song", cursor.getString(0))
                assertEquals(12, cursor.getInt(1))
                assertTrue(cursor.isNull(2))
                assertEquals(67, cursor.getInt(3))
                assertEquals(1, cursor.getInt(4))
                (5..8).forEach { assertTrue(cursor.isNull(it)) }
            }
        }
    }

    @Test
    fun `7 to 8 adds listen_event that keeps its rows when the track goes`() {
        helper.createDatabase(DB_NAME, 7).use { db ->
            db.execSQL(
                "INSERT INTO track (id, media_store_id, uri, title, title_key, artist_tag_values, duration_ms, " +
                    "date_modified, has_cover, has_lyrics, is_missing, is_damaged, rating_tenths, is_favorite) " +
                    "VALUES (1, 10, 'content://media/10', 'Song', 'song', '[\"A\"]', 1000, 500, " +
                    "0, 0, 0, 0, 67, 1)",
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 8, true, MIGRATION_7_8).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL(
                "INSERT INTO listen_event (track_id, started_at, played_ms, duration_ms, counted, skipped, " +
                    "title_snapshot, artists_snapshot) VALUES (1, 100, 900, 1000, 1, 0, 'Song', '[\"A\"]')",
            )
            db.execSQL("DELETE FROM track WHERE id = 1")
            db.query("SELECT track_id, title_snapshot, artists_snapshot, counted FROM listen_event").use { cursor ->
                cursor.moveToFirst()
                assertTrue(cursor.isNull(0))
                assertEquals("Song", cursor.getString(1))
                assertEquals("[\"A\"]", cursor.getString(2))
                assertEquals(1, cursor.getInt(3))
            }
        }
    }

    @Test
    fun `8 to 9 adds playlists that follow their tracks`() {
        helper.createDatabase(DB_NAME, 8).use { db ->
            db.execSQL(
                "INSERT INTO track (id, media_store_id, uri, title, title_key, artist_tag_values, duration_ms, " +
                    "date_modified, has_cover, has_lyrics, is_missing, is_damaged, rating_tenths, is_favorite) " +
                    "VALUES (1, 10, 'content://media/10', 'Song', 'song', '[\"A\"]', 1000, 500, 0, 0, 0, 0, 67, 1)",
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 9, true, MIGRATION_8_9).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("INSERT INTO playlist (id, name, created_at) VALUES (1, 'Mix', 5)")
            db.execSQL("INSERT INTO playlist_track (playlist_id, track_id, position) VALUES (1, 1, 0), (1, 1, 1)")
            db.query("SELECT COUNT(*) FROM playlist_track").use {
                it.moveToFirst()
                assertEquals(2, it.getInt(0))
            }
            db.execSQL("DELETE FROM track WHERE id = 1")
            db.query("SELECT COUNT(*), (SELECT name FROM playlist) FROM playlist_track").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
                assertEquals("Mix", it.getString(1))
            }
        }
    }

    @Test
    fun `9 to 10 dates the existing favorites with zero`() {
        helper.createDatabase(DB_NAME, 9).use { db ->
            db.execSQL(
                "INSERT INTO track (id, media_store_id, uri, title, title_key, artist_tag_values, duration_ms, " +
                    "date_modified, has_cover, has_lyrics, is_missing, is_damaged, rating_tenths, is_favorite) " +
                    "VALUES (1, 10, 'content://media/10', 'Song', 'song', '[\"A\"]', 1000, 500, 0, 0, 0, 0, 67, 1), " +
                    "(2, 11, 'content://media/11', 'Other', 'other', '[]', 1000, 500, 0, 0, 0, 0, NULL, 0)",
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 10, true, MIGRATION_9_10).use { db ->
            db.query("SELECT id, rating_tenths, is_favorite, favorited_at FROM track ORDER BY id").use {
                it.moveToFirst()
                assertEquals(67, it.getInt(1))
                assertEquals(1, it.getInt(2))
                assertEquals(0L, it.getLong(3))
                it.moveToNext()
                assertEquals(0, it.getInt(2))
                assertTrue(it.isNull(3))
            }
        }
    }

    @Test
    fun `10 to 11 drops every cached accent`() {
        helper.createDatabase(DB_NAME, 10).use { db ->
            db.execSQL(
                "INSERT INTO album (id, title, title_key, accent_color) " +
                    "VALUES (1, 'Grey', 'grey', 0), (2, 'Blue', 'blue', -14531363), (3, 'New', 'new', NULL)",
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 11, true, MIGRATION_10_11).use { db ->
            db.query("SELECT COUNT(*), COUNT(accent_color) FROM album").use {
                it.moveToFirst()
                assertEquals(3, it.getInt(0))
                assertEquals(0, it.getInt(1))
            }
        }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
