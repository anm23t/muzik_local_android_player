package org.muzzzlo.player.core.playback

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
}
