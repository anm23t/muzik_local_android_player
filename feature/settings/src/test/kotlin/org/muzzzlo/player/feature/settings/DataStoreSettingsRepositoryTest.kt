package org.muzzzlo.player.feature.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.muzzzlo.player.core.model.AppLanguage
import org.muzzzlo.player.core.model.AppSettings
import org.muzzzlo.player.core.model.CrossfadeSettings
import org.muzzzlo.player.core.model.PlaybackBehaviorSettings
import org.muzzzlo.player.core.model.ReplayGainMode
import org.muzzzlo.player.core.model.ReplayGainSettings
import org.muzzzlo.player.core.model.StatsSettings
import org.muzzzlo.player.core.model.TransientFocusAction
import java.io.File

class DataStoreSettingsRepositoryTest {
    @TempDir lateinit var directory: File

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val store by lazy {
        PreferenceDataStoreFactory.create(scope = scope) { File(directory, "settings.preferences_pb") }
    }
    private val repository by lazy { DataStoreSettingsRepository(store) }

    @AfterEach
    fun tearDown() = scope.cancel()

    @Test
    fun `an empty store reads as the defaults`() =
        runBlocking {
            assertEquals(AppSettings(), repository.settings.first())
        }

    @Test
    fun `every setting is saved and read back`() =
        runBlocking {
            repository.setFolderIncluded("Music/Podcasts/", included = false)
            repository.setFolderIncluded("", included = false)
            repository.setMinTrackSeconds(60)
            repository.setId3v2VersionForNewTags(3)
            repository.setConfirmBeforeWrite(false)
            repository.setLanguage(AppLanguage.RUSSIAN)
            repository.setDynamicAccent(false)
            repository.setGapless(false)
            val replayGain = ReplayGainSettings(ReplayGainMode.ALBUM, 2.5f, -10f, clipProtection = false)
            repository.setReplayGain(replayGain)
            repository.setCrossfade(CrossfadeSettings(seconds = 6, withinAlbum = true))
            val behavior =
                PlaybackBehaviorSettings(TransientFocusAction.PAUSE, resumeOnHeadset = true, skipSilence = true)
            repository.setPlaybackBehavior(behavior)
            val stats = StatsSettings(thresholdPercent = 70, thresholdMinutes = 2, privateMode = true)
            repository.setStats(stats)

            val expected =
                AppSettings(
                    excludedFolders = setOf("Music/Podcasts/", ""),
                    minTrackSeconds = 60,
                    id3v2VersionForNewTags = 3,
                    confirmBeforeWrite = false,
                    language = AppLanguage.RUSSIAN,
                    dynamicAccent = false,
                    gapless = false,
                    replayGain = replayGain,
                    crossfade = CrossfadeSettings(seconds = 6, withinAlbum = true),
                    playbackBehavior = behavior,
                    stats = stats,
                )
            assertEquals(expected, repository.settings.first())
        }

    @Test
    fun `including a folder again removes it from the excluded set`() =
        runBlocking {
            repository.setFolderIncluded("A/", included = false)
            repository.setFolderIncluded("B/", included = false)
            repository.setFolderIncluded("A/", included = true)
            repository.setFolderIncluded("C/", included = true)
            assertEquals(setOf("B/"), repository.settings.first().excludedFolders)
        }

    @ParameterizedTest
    @CsvSource("-1, 0", "0, 0", "45, 45", "9999, 600")
    fun `the minimum length is clamped when saved`(
        input: Int,
        expected: Int,
    ) = runBlocking {
        repository.setMinTrackSeconds(input)
        assertEquals(expected, repository.settings.first().minTrackSeconds)
    }

    @ParameterizedTest
    @CsvSource("-20, 3.3, -15, 0", "16, -0.2, 15, 0", "1.26, -6.24, 1.5, -6", "0, 4, 0, 0")
    fun `ReplayGain decibels are stepped and clamped when saved`(
        preamp: Float,
        untagged: Float,
        expectedPreamp: Float,
        expectedUntagged: Float,
    ) = runBlocking {
        repository.setReplayGain(ReplayGainSettings(preampDb = preamp, untaggedGainDb = untagged))
        val saved = repository.settings.first().replayGain
        assertEquals(expectedPreamp, saved.preampDb)
        assertEquals(expectedUntagged, saved.untaggedGainDb)
    }

    @ParameterizedTest
    @CsvSource("-3, 0", "0, 0", "12, 12", "13, 12")
    fun `the crossfade length is clamped when saved`(
        input: Int,
        expected: Int,
    ) = runBlocking {
        repository.setCrossfade(CrossfadeSettings(seconds = input))
        assertEquals(expected, repository.settings.first().crossfade.seconds)
    }

    @ParameterizedTest
    @CsvSource("5, 0, 10, 1", "95, 11, 90, 10", "44, 3, 40, 3")
    fun `the listen threshold is clamped when saved`(
        percent: Int,
        minutes: Int,
        expectedPercent: Int,
        expectedMinutes: Int,
    ) = runBlocking {
        repository.setStats(StatsSettings(thresholdPercent = percent, thresholdMinutes = minutes))
        assertEquals(StatsSettings(expectedPercent, expectedMinutes), repository.settings.first().stats)
    }

    @ParameterizedTest
    @CsvSource("2", "5", "0")
    fun `an unsupported ID3 version is refused`(version: Int) {
        assertThrows(
            IllegalArgumentException::class.java,
        ) { runBlocking { repository.setId3v2VersionForNewTags(version) } }
    }

    @Test
    fun `stored garbage reads as valid values`() =
        runBlocking {
            store.edit {
                it[DataStoreSettingsRepository.ID3V2_VERSION] = 7
                it[DataStoreSettingsRepository.MIN_TRACK_SECONDS] = -20
                it[DataStoreSettingsRepository.LANGUAGE] = "KLINGON"
                it[DataStoreSettingsRepository.REPLAY_GAIN_MODE] = "LOUDER"
                it[DataStoreSettingsRepository.REPLAY_GAIN_PREAMP_DB] = 99f
                it[DataStoreSettingsRepository.REPLAY_GAIN_UNTAGGED_DB] = Float.NaN
                it[DataStoreSettingsRepository.CROSSFADE_SECONDS] = 40
                it[DataStoreSettingsRepository.TRANSIENT_FOCUS] = "SHOUT"
                it[DataStoreSettingsRepository.STATS_THRESHOLD_PERCENT] = 0
                it[DataStoreSettingsRepository.STATS_THRESHOLD_MINUTES] = 600
            }
            val settings = repository.settings.first()
            assertEquals(4, settings.id3v2VersionForNewTags)
            assertEquals(0, settings.minTrackSeconds)
            assertEquals(AppLanguage.SYSTEM, settings.language)
            assertEquals(ReplayGainSettings(preampDb = 15f, untaggedGainDb = 0f), settings.replayGain)
            assertEquals(CrossfadeSettings(seconds = 12), settings.crossfade)
            assertEquals(TransientFocusAction.DUCK, settings.playbackBehavior.transientFocus)
            assertEquals(StatsSettings(thresholdPercent = 10, thresholdMinutes = 10), settings.stats)
        }

    @Test
    fun `toSettings of empty preferences is the default`() {
        assertEquals(AppSettings(), DataStoreSettingsRepository.toSettings(emptyPreferences()))
    }
}
