package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AutoPlaylistTest {
    @Test
    fun `every playlist is found again by its key`() {
        AutoPlaylist.entries.forEach { assertEquals(it, AutoPlaylist.fromKey(it.key)) }
    }

    @Test
    fun `keys are unique`() {
        assertEquals(AutoPlaylist.entries.size, AutoPlaylist.entries.map { it.key }.toSet().size)
    }

    @Test
    fun `an unknown or missing key gives nothing`() {
        assertNull(AutoPlaylist.fromKey("recent"))
        assertNull(AutoPlaylist.fromKey(""))
        assertNull(AutoPlaylist.fromKey(null))
    }
}
