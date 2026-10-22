package org.muzzzlo.player.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.muzzzlo.player.core.model.UpdateCheckState
import org.muzzzlo.player.core.model.UpdateCheckStore
import javax.inject.Inject

/** [UpdateCheckStore] in the same DataStore file as the settings, under its own keys. */
class DataStoreUpdateCheckStore
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
    ) : UpdateCheckStore {
        override val state: Flow<UpdateCheckState> =
            store.data.map {
                UpdateCheckState(
                    enabled = it[ENABLED],
                    lastCheckMs = it[LAST_CHECK],
                    announcedTag = it[ANNOUNCED_TAG],
                )
            }

        override suspend fun setEnabled(enabled: Boolean) {
            store.edit { it[ENABLED] = enabled }
        }

        override suspend fun setChecked(atMs: Long) {
            store.edit { it[LAST_CHECK] = atMs }
        }

        override suspend fun setAnnounced(tag: String) {
            store.edit { it[ANNOUNCED_TAG] = tag }
        }

        private companion object {
            val ENABLED = booleanPreferencesKey("update_check_enabled")
            val LAST_CHECK = longPreferencesKey("update_check_last_ms")
            val ANNOUNCED_TAG = stringPreferencesKey("update_check_announced_tag")
        }
    }
