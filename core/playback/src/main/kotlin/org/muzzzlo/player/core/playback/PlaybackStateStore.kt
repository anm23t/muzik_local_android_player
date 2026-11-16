package org.muzzzlo.player.core.playback

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
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
        suspend fun load(): SavedPlayback? = context.playbackStore.data.first().toSavedPlayback()

        suspend fun save(state: SavedPlayback) {
            context.playbackStore.edit { it.write(state) }
        }
    }

private val QUEUE = stringPreferencesKey("queue_ids")
private val INDEX = intPreferencesKey("index")
private val POSITION = longPreferencesKey("position_ms")
private val SHUFFLE = booleanPreferencesKey("shuffle")
private val REPEAT = intPreferencesKey("repeat")

/** Queue order before shuffle (task 2.5.3); missing in saves of older versions. */
private val ORIGINAL = stringPreferencesKey("original_ids")

/** The saved playback; null when no queue was saved. */
internal fun Preferences.toSavedPlayback(): SavedPlayback? {
    val ids = decodeIds(this[QUEUE].orEmpty())
    if (ids.isEmpty()) return null
    return SavedPlayback(
        trackIds = ids,
        index = this[INDEX] ?: 0,
        positionMs = this[POSITION] ?: 0,
        shuffleEnabled = this[SHUFFLE] ?: false,
        repeatMode = RepeatMode.entries.getOrElse(this[REPEAT] ?: 0) { RepeatMode.OFF },
        originalIds = this[ORIGINAL]?.let(::decodeIds)?.takeIf { it.isNotEmpty() },
    )
}

internal fun MutablePreferences.write(state: SavedPlayback) {
    this[QUEUE] = encodeIds(state.trackIds)
    this[INDEX] = state.index
    this[POSITION] = state.positionMs
    this[SHUFFLE] = state.shuffleEnabled
    this[REPEAT] = state.repeatMode.ordinal
    this[ORIGINAL] = encodeIds(state.originalIds.orEmpty())
}
