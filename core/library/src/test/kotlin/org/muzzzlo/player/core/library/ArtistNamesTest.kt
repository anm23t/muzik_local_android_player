package org.muzzzlo.player.core.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ArtistNamesTest {
    @Test
    fun `empty, blank and unknown artist give no names`() {
        assertEquals(emptyList<String>(), artistNames(null))
        assertEquals(emptyList<String>(), artistNames("   "))
        assertEquals(emptyList<String>(), artistNames("<unknown>"))
    }

    @Test
    fun `artist is trimmed and kept whole`() {
        assertEquals(listOf("Simon & Garfunkel"), artistNames("  Simon & Garfunkel "))
    }

    @Test
    fun `unknown album is dropped`() {
        assertNull(cleanAlbum("<unknown>"))
        assertNull(cleanAlbum(" "))
        assertEquals("Album", cleanAlbum(" Album "))
    }
}
