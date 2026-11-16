package org.muzzzlo.player.feature.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.muzzzlo.player.core.model.AppLanguage
import org.muzzzlo.player.core.model.AppSettings
import org.muzzzlo.player.core.model.CrossfadeSettings
import org.muzzzlo.player.core.model.EqualizerPreset
import org.muzzzlo.player.core.model.EqualizerSettings
import org.muzzzlo.player.core.model.InterfaceTheme
import org.muzzzlo.player.core.model.PlaybackBehaviorSettings
import org.muzzzlo.player.core.model.ReplayGainMode
import org.muzzzlo.player.core.model.ReplayGainSettings
import org.muzzzlo.player.core.model.SettingsRepository
import org.muzzzlo.player.core.model.SortedList
import org.muzzzlo.player.core.model.StatsSettings
import org.muzzzlo.player.core.model.StoredSort
import org.muzzzlo.player.core.model.TransientFocusAction
import javax.inject.Inject

/**
 * The one DataStore file of the app settings. It lives here and not in the repository so that `MainActivity`
 * can read the language before Hilt is ready (see [LocaleContext]).
 */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * [SettingsRepository] on Preferences DataStore. A missing key reads as the default of [AppSettings];
 * values that are out of range or unknown (an older or newer version wrote them) are brought back to a valid one.
 */
class DataStoreSettingsRepository
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
    ) : SettingsRepository {
        override val settings: Flow<AppSettings> = store.data.map(::toSettings)

        override suspend fun setFolderIncluded(
            relativePath: String,
            included: Boolean,
        ) {
            store.edit {
                val excluded = it[EXCLUDED_FOLDERS].orEmpty()
                it[EXCLUDED_FOLDERS] = if (included) excluded - relativePath else excluded + relativePath
            }
        }

        override suspend fun setOnlySelectedFolders(enabled: Boolean) {
            store.edit { it[ONLY_SELECTED_FOLDERS] = enabled }
        }

        override suspend fun setFolderSelected(
            relativePath: String,
            selected: Boolean,
        ) {
            store.edit {
                val current = it[SELECTED_FOLDERS].orEmpty()
                it[SELECTED_FOLDERS] = if (selected) current + relativePath else current - relativePath
            }
        }

        override suspend fun setMinTrackSeconds(seconds: Int) {
            store.edit { it[MIN_TRACK_SECONDS] = AppSettings.clampMinTrackSeconds(seconds) }
        }

        override suspend fun setId3v2VersionForNewTags(version: Int) {
            require(version in AppSettings.ID3V2_VERSIONS) { "ID3v2 version must be 3 or 4: $version" }
            store.edit { it[ID3V2_VERSION] = version }
        }

        override suspend fun setConfirmBeforeWrite(enabled: Boolean) {
            store.edit { it[CONFIRM_BEFORE_WRITE] = enabled }
        }

        override suspend fun setLanguage(language: AppLanguage) {
            store.edit { it[LANGUAGE] = language.name }
        }

        override suspend fun setDynamicAccent(enabled: Boolean) {
            store.edit { it[DYNAMIC_ACCENT] = enabled }
        }

        override suspend fun setGapless(enabled: Boolean) {
            store.edit { it[GAPLESS] = enabled }
        }

        override suspend fun setReplayGain(settings: ReplayGainSettings) {
            val clamped = settings.clamped()
            store.edit {
                it[REPLAY_GAIN_MODE] = clamped.mode.name
                it[REPLAY_GAIN_PREAMP_DB] = clamped.preampDb
                it[REPLAY_GAIN_UNTAGGED_DB] = clamped.untaggedGainDb
                it[REPLAY_GAIN_CLIP_PROTECTION] = clamped.clipProtection
            }
        }

        override suspend fun setCrossfade(settings: CrossfadeSettings) {
            val clamped = settings.clamped()
            store.edit {
                it[CROSSFADE_SECONDS] = clamped.seconds
                it[CROSSFADE_WITHIN_ALBUM] = clamped.withinAlbum
            }
        }

        override suspend fun setPlaybackBehavior(settings: PlaybackBehaviorSettings) {
            store.edit {
                it[TRANSIENT_FOCUS] = settings.transientFocus.name
                it[RESUME_ON_HEADSET] = settings.resumeOnHeadset
                it[SKIP_SILENCE] = settings.skipSilence
            }
        }

        override suspend fun setStats(settings: StatsSettings) {
            val clamped = settings.clamped()
            store.edit {
                it[STATS_THRESHOLD_PERCENT] = clamped.thresholdPercent
                it[STATS_THRESHOLD_MINUTES] = clamped.thresholdMinutes
                it[STATS_PRIVATE_MODE] = clamped.privateMode
            }
        }

        override suspend fun setTheme(theme: InterfaceTheme) {
            store.edit { it[THEME] = theme.name }
        }

        override suspend fun setLyricsFolder(value: String?) {
            store.edit { if (value == null) it.remove(LYRICS_FOLDER) else it[LYRICS_FOLDER] = value }
        }

        override suspend fun setLyricsShown(value: Boolean) {
            store.edit { it[LYRICS_SHOWN] = value }
        }

        override suspend fun setWaveform(enabled: Boolean) {
            store.edit { it[WAVEFORM] = enabled }
        }

        override suspend fun setAnimatedBackground(enabled: Boolean) {
            store.edit { it[ANIMATED_BACKGROUND] = enabled }
        }

        override suspend fun setFolderCovers(enabled: Boolean) {
            store.edit { it[FOLDER_COVERS] = enabled }
        }

        override suspend fun setEqualizer(settings: EqualizerSettings) {
            val clamped = settings.clamped()
            store.edit {
                it[EQUALIZER_ENABLED] = clamped.enabled
                it[EQUALIZER_PRESET] = clamped.preset.name
                it[EQUALIZER_CUSTOM_DB] = clamped.customDb.joinToString(",")
                it[EQUALIZER_PREAMP_DB] = clamped.preampDb
            }
        }

        override suspend fun setListSort(
            list: SortedList,
            sort: StoredSort,
        ) {
            store.edit {
                it[sortFieldKey(list)] = sort.field
                it[sortAscendingKey(list)] = sort.ascending
            }
        }

        internal companion object {
            val EXCLUDED_FOLDERS = stringSetPreferencesKey("excluded_folders")
            val ONLY_SELECTED_FOLDERS = booleanPreferencesKey("only_selected_folders")
            val SELECTED_FOLDERS = stringSetPreferencesKey("selected_folders")
            val MIN_TRACK_SECONDS = intPreferencesKey("min_track_seconds")
            val ID3V2_VERSION = intPreferencesKey("id3v2_version_for_new_tags")
            val CONFIRM_BEFORE_WRITE = booleanPreferencesKey("confirm_before_write")
            val LANGUAGE = stringPreferencesKey("language")
            val DYNAMIC_ACCENT = booleanPreferencesKey("dynamic_accent")
            val GAPLESS = booleanPreferencesKey("gapless")
            val REPLAY_GAIN_MODE = stringPreferencesKey("replay_gain_mode")
            val REPLAY_GAIN_PREAMP_DB = floatPreferencesKey("replay_gain_preamp_db")
            val REPLAY_GAIN_UNTAGGED_DB = floatPreferencesKey("replay_gain_untagged_db")
            val REPLAY_GAIN_CLIP_PROTECTION = booleanPreferencesKey("replay_gain_clip_protection")
            val CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
            val CROSSFADE_WITHIN_ALBUM = booleanPreferencesKey("crossfade_within_album")
            val TRANSIENT_FOCUS = stringPreferencesKey("transient_focus")
            val RESUME_ON_HEADSET = booleanPreferencesKey("resume_on_headset")
            val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
            val STATS_THRESHOLD_PERCENT = intPreferencesKey("stats_threshold_percent")
            val STATS_THRESHOLD_MINUTES = intPreferencesKey("stats_threshold_minutes")
            val STATS_PRIVATE_MODE = booleanPreferencesKey("stats_private_mode")
            val THEME = stringPreferencesKey("theme")
            val WAVEFORM = booleanPreferencesKey("waveform")
            val ANIMATED_BACKGROUND = booleanPreferencesKey("animated_background")

            /** Not in the backup: it belongs to this device. */
            val LYRICS_SHOWN = booleanPreferencesKey("lyrics_shown")

            /** Not in the backup: it belongs to this device. */
            val LYRICS_FOLDER = stringPreferencesKey("lyrics_folder")
            val FOLDER_COVERS = booleanPreferencesKey("folder_covers")
            val EQUALIZER_ENABLED = booleanPreferencesKey("equalizer_enabled")
            val EQUALIZER_PRESET = stringPreferencesKey("equalizer_preset")

            /** Gains of the custom curve in dB, comma separated, one per band. */
            val EQUALIZER_CUSTOM_DB = stringPreferencesKey("equalizer_custom_db")
            val EQUALIZER_PREAMP_DB = floatPreferencesKey("equalizer_preamp_db")

            fun sortFieldKey(list: SortedList) = stringPreferencesKey("sort_${list.name.lowercase()}_field")

            fun sortAscendingKey(list: SortedList) = booleanPreferencesKey("sort_${list.name.lowercase()}_ascending")

            private fun sortsOf(preferences: Preferences): Map<SortedList, StoredSort> =
                SortedList.entries.mapNotNull { list ->
                    val field = preferences[sortFieldKey(list)] ?: return@mapNotNull null
                    val ascending = preferences[sortAscendingKey(list)] ?: return@mapNotNull null
                    list to StoredSort(field, ascending)
                }.toMap()

            fun toSettings(preferences: Preferences): AppSettings {
                val defaults = AppSettings()
                return AppSettings(
                    excludedFolders = preferences[EXCLUDED_FOLDERS].orEmpty(),
                    onlySelectedFolders = preferences[ONLY_SELECTED_FOLDERS] ?: false,
                    selectedFolders = preferences[SELECTED_FOLDERS].orEmpty(),
                    minTrackSeconds = minTrackSecondsOf(preferences, defaults),
                    id3v2VersionForNewTags = id3v2VersionOf(preferences, defaults),
                    confirmBeforeWrite = preferences[CONFIRM_BEFORE_WRITE] ?: defaults.confirmBeforeWrite,
                    language =
                        AppLanguage.entries.firstOrNull { it.name == preferences[LANGUAGE] } ?: defaults.language,
                    dynamicAccent = preferences[DYNAMIC_ACCENT] ?: defaults.dynamicAccent,
                    gapless = preferences[GAPLESS] ?: defaults.gapless,
                    replayGain = replayGainOf(preferences, defaults.replayGain),
                    crossfade =
                        CrossfadeSettings(
                            seconds = preferences[CROSSFADE_SECONDS] ?: defaults.crossfade.seconds,
                            withinAlbum = preferences[CROSSFADE_WITHIN_ALBUM] ?: defaults.crossfade.withinAlbum,
                        ).clamped(),
                    playbackBehavior = playbackBehaviorOf(preferences, defaults.playbackBehavior),
                    stats =
                        StatsSettings(
                            thresholdPercent = preferences[STATS_THRESHOLD_PERCENT] ?: defaults.stats.thresholdPercent,
                            thresholdMinutes = preferences[STATS_THRESHOLD_MINUTES] ?: defaults.stats.thresholdMinutes,
                            privateMode = preferences[STATS_PRIVATE_MODE] ?: defaults.stats.privateMode,
                        ).clamped(),
                    theme = InterfaceTheme.entries.firstOrNull { it.name == preferences[THEME] } ?: defaults.theme,
                    waveform = preferences[WAVEFORM] ?: defaults.waveform,
                    equalizer = equalizerOf(preferences, defaults.equalizer),
                    listSorts = sortsOf(preferences),
                ).withVersion2(preferences)
            }

            /** The settings added in version 2.0, read apart so that [toSettings] stays readable. */
            private fun AppSettings.withVersion2(preferences: Preferences): AppSettings =
                copy(
                    folderCovers = preferences[FOLDER_COVERS] ?: folderCovers,
                    lyricsFolder = preferences[LYRICS_FOLDER],
                    lyricsShown = preferences[LYRICS_SHOWN] ?: lyricsShown,
                    animatedBackground = preferences[ANIMATED_BACKGROUND] ?: animatedBackground,
                )

            private fun minTrackSecondsOf(
                preferences: Preferences,
                defaults: AppSettings,
            ) = preferences[MIN_TRACK_SECONDS]?.let(AppSettings::clampMinTrackSeconds) ?: defaults.minTrackSeconds

            private fun id3v2VersionOf(
                preferences: Preferences,
                defaults: AppSettings,
            ): Int {
                val stored = preferences[ID3V2_VERSION]?.takeIf { it in AppSettings.ID3V2_VERSIONS }
                return stored ?: defaults.id3v2VersionForNewTags
            }

            private fun equalizerOf(
                preferences: Preferences,
                defaults: EqualizerSettings,
            ) = EqualizerSettings(
                enabled = preferences[EQUALIZER_ENABLED] ?: defaults.enabled,
                preset =
                    EqualizerPreset.entries.firstOrNull { it.name == preferences[EQUALIZER_PRESET] } ?: defaults.preset,
                customDb =
                    preferences[EQUALIZER_CUSTOM_DB]?.split(',')?.map { it.trim().toFloatOrNull() ?: 0f }
                        ?: defaults.customDb,
                preampDb = preferences[EQUALIZER_PREAMP_DB] ?: defaults.preampDb,
            ).clamped()

            private fun playbackBehaviorOf(
                preferences: Preferences,
                defaults: PlaybackBehaviorSettings,
            ) = PlaybackBehaviorSettings(
                transientFocus =
                    TransientFocusAction.entries.firstOrNull { it.name == preferences[TRANSIENT_FOCUS] }
                        ?: defaults.transientFocus,
                resumeOnHeadset = preferences[RESUME_ON_HEADSET] ?: defaults.resumeOnHeadset,
                skipSilence = preferences[SKIP_SILENCE] ?: defaults.skipSilence,
            )

            private fun replayGainOf(
                preferences: Preferences,
                defaults: ReplayGainSettings,
            ) = ReplayGainSettings(
                mode = ReplayGainMode.entries.firstOrNull { it.name == preferences[REPLAY_GAIN_MODE] } ?: defaults.mode,
                preampDb = preferences[REPLAY_GAIN_PREAMP_DB] ?: defaults.preampDb,
                untaggedGainDb = preferences[REPLAY_GAIN_UNTAGGED_DB] ?: defaults.untaggedGainDb,
                clipProtection = preferences[REPLAY_GAIN_CLIP_PROTECTION] ?: defaults.clipProtection,
            ).clamped()
        }
    }
