package org.muzzzlo.player.core.playback

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.muzzzlo.player.core.model.RepeatMode
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playbackStore by preferencesDataStore(name = "playback_state")

/** Keeps the queue and the position in DataStore so playback can be resumed after a restart. */
@Singleton
internal class PlaybackStateStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend fun load(): SavedPlayback? {
            val prefs = context.playbackStore.data.first()
            val ids = decodeIds(prefs[QUEUE].orEmpty())
            if (ids.isEmpty()) return null
            return SavedPlayback(
                trackIds = ids,
                index = prefs[INDEX] ?: 0,
                positionMs = prefs[POSITION] ?: 0,
                shuffleEnabled = prefs[SHUFFLE] ?: false,
                repeatMode = RepeatMode.entries.getOrElse(prefs[REPEAT] ?: 0) { RepeatMode.OFF },
            )
        }

        suspend fun save(state: SavedPlayback) {
            context.playbackStore.edit {
                it[QUEUE] = encodeIds(state.trackIds)
                it[INDEX] = state.index
                it[POSITION] = state.positionMs
                it[SHUFFLE] = state.shuffleEnabled
                it[REPEAT] = state.repeatMode.ordinal
            }
        }

        private companion object {
            val QUEUE = stringPreferencesKey("queue_ids")
            val INDEX = intPreferencesKey("index")
            val POSITION = longPreferencesKey("position_ms")
            val SHUFFLE = booleanPreferencesKey("shuffle")
            val REPEAT = intPreferencesKey("repeat")
        }
    }
