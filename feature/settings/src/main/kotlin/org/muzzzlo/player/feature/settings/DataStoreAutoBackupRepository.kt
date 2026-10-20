package org.muzzzlo.player.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.muzzzlo.player.core.model.AutoBackupRepository
import org.muzzzlo.player.core.model.AutoBackupSettings
import javax.inject.Inject

/** [AutoBackupRepository] in the same DataStore file as the settings, under its own keys. */
class DataStoreAutoBackupRepository
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
    ) : AutoBackupRepository {
        override val settings: Flow<AutoBackupSettings> =
            store.data.map {
                AutoBackupSettings(
                    enabled = it[ENABLED] ?: false,
                    folderUri = it[FOLDER],
                    lastRunMs = it[LAST_RUN],
                    lastRunFailed = it[LAST_FAILED] ?: false,
                )
            }

        override suspend fun setEnabled(enabled: Boolean) {
            store.edit { it[ENABLED] = enabled }
        }

        override suspend fun setFolder(uri: String?) {
            store.edit {
                if (uri == null) it.remove(FOLDER) else it[FOLDER] = uri
                // A result belongs to the old folder.
                it.remove(LAST_RUN)
                it.remove(LAST_FAILED)
            }
        }

        override suspend fun recordResult(
            timeMs: Long,
            failed: Boolean,
        ) {
            store.edit {
                it[LAST_RUN] = timeMs
                it[LAST_FAILED] = failed
            }
        }

        internal companion object {
            val ENABLED = booleanPreferencesKey("auto_backup_enabled")
            val FOLDER = stringPreferencesKey("auto_backup_folder")
            val LAST_RUN = longPreferencesKey("auto_backup_last_run")
            val LAST_FAILED = booleanPreferencesKey("auto_backup_last_failed")
        }
    }
