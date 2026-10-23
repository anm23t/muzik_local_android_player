package org.muzzzlo.player.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.muzzzlo.player.core.database.backup.DefaultBackupRepository
import org.muzzzlo.player.core.database.entity.ArtistAliasEntity
import org.muzzzlo.player.core.database.entity.ArtistEntity
import org.muzzzlo.player.core.database.entity.ListenEventEntity
import org.muzzzlo.player.core.database.entity.PlaylistEntity
import org.muzzzlo.player.core.database.entity.TrackEntity
import org.muzzzlo.player.core.model.AppLanguage
import org.muzzzlo.player.core.model.AppSettings
import org.muzzzlo.player.core.model.BackupFormatException
import org.muzzzlo.player.core.model.CompoundRepository
import org.muzzzlo.player.core.model.CompoundRule
import org.muzzzlo.player.core.model.CrossfadeSettings
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.EqualizerSettings
import org.muzzzlo.player.core.model.InterfaceTheme
import org.muzzzlo.player.core.model.PlaybackBehaviorSettings
import org.muzzzlo.player.core.model.ReplayGainSettings
import org.muzzzlo.player.core.model.SettingsRepository
import org.muzzzlo.player.core.model.StatsSettings
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class DefaultBackupRepositoryTest {
    private lateinit var db: MuzzzloDatabase
    private val settings = FakeSettings()
    private val compounds = FakeCompounds()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MuzzzloDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun repository(database: MuzzzloDatabase = db): DefaultBackupRepository =
        DefaultBackupRepository(
            database,
            database.libraryDao(),
            database.listenEventDao(),
            database.backupDao(),
            database.playlistDao(),
            compounds,
            settings,
            object : DispatcherProvider {
                override val io: CoroutineDispatcher = Dispatchers.Unconfined
                override val main: CoroutineDispatcher = Dispatchers.Unconfined
                override val default: CoroutineDispatcher = Dispatchers.Unconfined
            },
            Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC),
        )

    private suspend fun track(
        title: String,
        tags: String = "[\"A\"]",
        rating: Int? = null,
        favorite: Boolean = false,
        favoritedAt: Long? = null,
        target: MuzzzloDatabase = db,
        durationMs: Long = 200_000,
    ): Long =
        target.libraryDao().insertTrack(
            TrackEntity(
                mediaStoreId = null,
                uri = "content://$title",
                title = title,
                titleKey = title.lowercase(),
                artistTagValues = tags,
                durationMs = durationMs,
                ratingTenths = rating,
                isFavorite = favorite,
                favoritedAt = favoritedAt,
            ),
        )

    private suspend fun event(
        trackId: Long?,
        at: Long,
        title: String = "Song",
    ) = db.listenEventDao().insert(
        ListenEventEntity(
            trackId = trackId,
            startedAt = at,
            playedMs = 100,
            durationMs = 200_000,
            counted = true,
            skipped = false,
            titleSnapshot = title,
            artistsSnapshot = "[\"A\",\"B\"]",
        ),
    )

    private fun fresh(): MuzzzloDatabase =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MuzzzloDatabase::class.java)
            .allowMainThreadQueries().build()

    @Test
    fun `export then import into an empty library restores marks events aliases and settings`() =
        runBlocking {
            val artist = db.libraryDao().insertArtist(ArtistEntity(name = "Tyler", nameKey = "tyler"))
            db.libraryDao().insertAlias(
                ArtistAliasEntity(aliasKey = "tyler 2", aliasDisplay = "Tyler 2", artistId = artist),
            )
            val id = track("Song", "[\"A\",\"B\"]", rating = 67, favorite = true, favoritedAt = 1234)
            track("Unrated")
            event(id, 10)
            event(null, 20, "Deleted track")
            settings.state.value =
                AppSettings(language = AppLanguage.RUSSIAN, stats = StatsSettings(thresholdPercent = 30))
            compounds.rules += CompoundRule(1, "Tyler, The Creator", null, true)
            compounds.rules += CompoundRule(2, "Built, In", "b1", false)
            val json = repository().exportJson()

            val other = fresh()
            val newId = track("song", "[\"a\",\"b\"]", target = other, durationMs = 200_500)
            settings.state.value = AppSettings()
            compounds.rules.clear()
            compounds.rules += CompoundRule(2, "Built, In", "b1", true)
            val result = repository(other).importJson(json)

            assertEquals(1, result.aliases)
            assertEquals(1, result.marks)
            assertEquals(0, result.unmatchedMarks)
            assertEquals(2, result.events)
            assertEquals(2, result.compounds)
            val row = other.backupDao().trackIdentities().single()
            assertEquals(67, row.ratingTenths)
            assertTrue(row.isFavorite)
            assertEquals(1234L, row.favoritedAt)
            val events = other.listenEventDao().all()
            assertEquals(listOf(null, newId), events.map { it.trackId })
            assertEquals(listOf("Tyler, The Creator"), compounds.remembered)
            assertEquals(listOf(2L), compounds.removed)
            assertEquals(AppLanguage.RUSSIAN, settings.state.value.language)
            assertEquals(30, settings.state.value.stats.thresholdPercent)
            assertEquals("Tyler", other.backupDao().aliases().single().artistName)
            other.close()
        }

    @Test
    fun `importing the same backup twice adds nothing the second time`() =
        runBlocking {
            val id = track("Song", rating = 50)
            event(id, 10)
            val json = repository().exportJson()
            val other = fresh()
            track("Song", target = other)
            repository(other).importJson(json)
            val second = repository(other).importJson(json)
            assertEquals(0, second.events)
            assertEquals(0, second.marks)
            assertEquals(1, other.listenEventDao().all().size)
            other.close()
        }

    @Test
    fun `import keeps an existing rating and adds a favorite`() =
        runBlocking {
            track("Song", rating = 30)
            val json = repository().exportJson()
            val other = fresh()
            val id = track("Song", rating = 90, target = other)
            repository(other).importJson(json.replace("\"favorite\": false", "\"favorite\": true"))
            val row = other.backupDao().trackIdentities().single { it.id == id }
            assertEquals(90, row.ratingTenths)
            assertTrue(row.isFavorite)
            // The backup does not say when: the favorite comes after those with a known moment.
            assertEquals(0L, row.favoritedAt)
            other.close()
        }

    @Test
    fun `marks of tracks missing from the library are counted as unmatched`() =
        runBlocking {
            track("Gone", rating = 10)
            val json = repository().exportJson()
            val other = fresh()
            val result = repository(other).importJson(json)
            assertEquals(1, result.unmatchedMarks)
            assertEquals(0, result.marks)
            other.close()
        }

    @Test
    fun `playlists round trip in order with repeats and missing tracks dropped`() =
        runBlocking {
            val a = track("A")
            val b = track("B")
            val gone = track("Gone")
            val mix = db.playlistDao().insertPlaylist(PlaylistEntity(name = "Mix", createdAt = 5, lastPlayedAt = 9))
            db.playlistDao().append(mix, listOf(b, a, gone, b))
            db.playlistDao().insertPlaylist(PlaylistEntity(name = "Empty", createdAt = 6))
            val json = repository().exportJson()

            val other = fresh()
            track("a", target = other)
            track("b", target = other)
            val result = repository(other).importJson(json)

            assertEquals(2, result.playlists)
            assertEquals(1, result.unmatchedPlaylistTracks)
            val rows = other.playlistDao().observePlaylists().first()
            assertEquals(listOf("Empty", "Mix"), rows.map { it.name })
            val imported = rows.single { it.name == "Mix" }
            assertEquals(9L, imported.lastPlayedAt)
            assertEquals(5L, imported.createdAt)
            val titles = other.playlistDao().observeEntries(imported.id).first().map { it.track.title }
            assertEquals(listOf("b", "a", "b"), titles)

            val second = repository(other).importJson(json)
            assertEquals(0, second.playlists)
            assertEquals(2, other.playlistDao().observePlaylists().first().size)
            other.close()
        }

    @Test
    fun `a playlist with the same name but another creation time is added`() =
        runBlocking {
            db.playlistDao().insertPlaylist(PlaylistEntity(name = "Mix", createdAt = 5))
            val json = repository().exportJson()
            val other = fresh()
            other.playlistDao().insertPlaylist(PlaylistEntity(name = "Mix", createdAt = 7))

            assertEquals(1, repository(other).importJson(json).playlists)
            assertEquals(2, other.playlistDao().observePlaylists().first().size)
            other.close()
        }

    @Test
    fun `a version 1 backup without playlists still imports`() =
        runBlocking {
            val json = repository().exportJson().replace("\"schemaVersion\": 2", "\"schemaVersion\": 1")
            assertTrue(json.contains("\"schemaVersion\": 1"))

            assertEquals(0, repository().importJson(json).playlists)
        }

    @Test
    fun `a bad file changes nothing`() {
        assertThrows(BackupFormatException::class.java) { runBlocking { repository().importJson("{\"x\":1}") } }
        assertNull(runBlocking { db.listenEventDao().all().firstOrNull() })
        assertEquals(AppSettings(), settings.state.value)
    }

    @Test
    fun `csv export has one row per event`() =
        runBlocking {
            event(null, 5, "Tyler, The Creator")
            val csv = repository().exportEventsCsv()
            val lines = csv.trim().split("\r\n")
            assertEquals(2, lines.size)
            assertTrue(lines[1].startsWith("1970-01-01T00:00:00.005Z,100,200000,1,0,\"Tyler, The Creator\",A; B"))
        }

    private class FakeSettings : SettingsRepository {
        val state = MutableStateFlow(AppSettings())
        override val settings: Flow<AppSettings> = state

        override suspend fun setFolderIncluded(
            relativePath: String,
            included: Boolean,
        ) {
            state.value =
                state.value.copy(
                    excludedFolders =
                        state.value.excludedFolders.let { if (included) it - relativePath else it + relativePath },
                )
        }

        override suspend fun setMinTrackSeconds(seconds: Int) {
            state.value = state.value.copy(minTrackSeconds = seconds)
        }

        override suspend fun setId3v2VersionForNewTags(version: Int) {
            state.value = state.value.copy(id3v2VersionForNewTags = version)
        }

        override suspend fun setConfirmBeforeWrite(enabled: Boolean) {
            state.value = state.value.copy(confirmBeforeWrite = enabled)
        }

        override suspend fun setLanguage(language: AppLanguage) {
            state.value = state.value.copy(language = language)
        }

        override suspend fun setDynamicAccent(enabled: Boolean) {
            state.value = state.value.copy(dynamicAccent = enabled)
        }

        override suspend fun setGapless(enabled: Boolean) {
            state.value = state.value.copy(gapless = enabled)
        }

        override suspend fun setReplayGain(settings: ReplayGainSettings) {
            state.value = state.value.copy(replayGain = settings)
        }

        override suspend fun setCrossfade(settings: CrossfadeSettings) {
            state.value = state.value.copy(crossfade = settings)
        }

        override suspend fun setPlaybackBehavior(settings: PlaybackBehaviorSettings) {
            state.value = state.value.copy(playbackBehavior = settings)
        }

        override suspend fun setWaveform(enabled: Boolean) {
            state.value = state.value.copy(waveform = enabled)
        }

        override suspend fun setEqualizer(settings: EqualizerSettings) {
            state.value = state.value.copy(equalizer = settings)
        }

        override suspend fun setTheme(theme: InterfaceTheme) {
            state.value = state.value.copy(theme = theme)
        }

        override suspend fun setStats(settings: StatsSettings) {
            state.value = state.value.copy(stats = settings)
        }
    }

    private class FakeCompounds : CompoundRepository {
        val rules = mutableListOf<CompoundRule>()
        val remembered = mutableListOf<String>()
        val removed = mutableListOf<Long>()

        override fun observeRules(): Flow<List<CompoundRule>> = MutableStateFlow(rules.toList())

        override suspend fun compoundNames() = error("not used")

        override suspend fun syncBuiltIn() = Unit

        override suspend fun remember(name: String): Int {
            remembered += name
            return 1
        }

        override suspend fun remove(id: Long): Int {
            removed += id
            return 1
        }

        override suspend fun countAffectedBy(id: Long) = 0

        override suspend fun enable(id: Long) = 0
    }
}
