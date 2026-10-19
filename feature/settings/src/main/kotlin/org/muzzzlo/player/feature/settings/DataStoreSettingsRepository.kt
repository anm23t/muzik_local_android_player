package org.muzzzlo.player.feature.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.muzzzlo.player.core.model.AppLanguage
import org.muzzzlo.player.core.model.AppSettings
import org.muzzzlo.player.core.model.SettingsRepository
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

        internal companion object {
            val EXCLUDED_FOLDERS = stringSetPreferencesKey("excluded_folders")
            val MIN_TRACK_SECONDS = intPreferencesKey("min_track_seconds")
            val ID3V2_VERSION = intPreferencesKey("id3v2_version_for_new_tags")
            val CONFIRM_BEFORE_WRITE = booleanPreferencesKey("confirm_before_write")
            val LANGUAGE = stringPreferencesKey("language")
            val DYNAMIC_ACCENT = booleanPreferencesKey("dynamic_accent")
            val GAPLESS = booleanPreferencesKey("gapless")

            fun toSettings(preferences: Preferences): AppSettings {
                val defaults = AppSettings()
                return AppSettings(
                    excludedFolders = preferences[EXCLUDED_FOLDERS].orEmpty(),
                    minTrackSeconds =
                        preferences[MIN_TRACK_SECONDS]?.let(AppSettings::clampMinTrackSeconds)
                            ?: defaults.minTrackSeconds,
                    id3v2VersionForNewTags =
                        preferences[ID3V2_VERSION]?.takeIf { it in AppSettings.ID3V2_VERSIONS }
                            ?: defaults.id3v2VersionForNewTags,
                    confirmBeforeWrite = preferences[CONFIRM_BEFORE_WRITE] ?: defaults.confirmBeforeWrite,
                    language =
                        AppLanguage.entries.firstOrNull { it.name == preferences[LANGUAGE] } ?: defaults.language,
                    dynamicAccent = preferences[DYNAMIC_ACCENT] ?: defaults.dynamicAccent,
                    gapless = preferences[GAPLESS] ?: defaults.gapless,
                )
            }
        }
    }
