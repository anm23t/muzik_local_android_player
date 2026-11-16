package org.muzzzlo.player.core.playback

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.model.RepeatMode

class SavedPlaybackTest {
    private fun saved(
        ids: List<Long>,
        index: Int,
    ) = SavedPlayback(ids, index, positionMs = 1000, shuffleEnabled = false, repeatMode = RepeatMode.OFF)

    @Test
    fun `ids survive a round trip`() {
        val ids = listOf(5L, 1L, 99L)
        assertEquals(ids, decodeIds(encodeIds(ids)))
    }

    @Test
    fun `empty and broken input gives no ids`() {
        assertEquals(emptyList<Long>(), decodeIds(""))
        assertEquals(listOf(1L, 3L), decodeIds("1,x,,3"))
        assertEquals(listOf(7L), decodeIds(" 7 "))
    }

    @Test
    fun `index follows the track when earlier tracks disappeared`() {
        val saved = saved(listOf(10, 20, 30), index = 2)
        assertEquals(1, restoredIndex(saved, listOf(20, 30)))
    }

    @Test
    fun `index is null when the current track is gone`() {
        assertNull(restoredIndex(saved(listOf(10, 20), index = 1), listOf(10)))
    }

    @Test
    fun `index is null when saved index is out of range`() {
        assertNull(restoredIndex(saved(listOf(10), index = 5), listOf(10)))
    }

    @Test
    fun `duplicate tracks resolve to the first occurrence`() {
        assertEquals(0, restoredIndex(saved(listOf(10, 20, 10), index = 2), listOf(10, 20, 10)))
    }

    @Test
    fun `shuffled queue and its original order survive a restart`() {
        val state =
            SavedPlayback(
                trackIds = listOf(3, 1, 2),
                index = 0,
                positionMs = 4200,
                shuffleEnabled = true,
                repeatMode = RepeatMode.ALL,
                originalIds = listOf(1, 2, 3),
            )
        val prefs = mutablePreferencesOf().apply { write(state) }
        assertEquals(state, prefs.toSavedPlayback())
    }

    @Test
    fun `turning shuffle off clears the original order`() {
        val prefs = mutablePreferencesOf()
        prefs.write(saved(listOf(3, 1), index = 0).copy(shuffleEnabled = true, originalIds = listOf(1, 3)))
        prefs.write(saved(listOf(1, 3), index = 1))
        assertNull(prefs.toSavedPlayback()?.originalIds)
    }

    @Test
    fun `a save of an older version has no original order`() {
        val prefs =
            mutablePreferencesOf(stringPreferencesKey("queue_ids") to "5,6", booleanPreferencesKey("shuffle") to true)
        val saved = prefs.toSavedPlayback()
        assertEquals(listOf(5L, 6L), saved?.trackIds)
        assertNull(saved?.originalIds)
    }

    @Test
    fun `nothing saved gives null`() {
        assertNull(mutablePreferencesOf().toSavedPlayback())
    }
}
