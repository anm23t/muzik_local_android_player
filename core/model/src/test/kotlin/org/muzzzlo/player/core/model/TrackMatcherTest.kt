package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TrackMatcherTest {
    private data class T(
        val id: Int,
        val title: String,
        val artists: List<String>,
        val durationMs: Long,
    )

    private val tracks =
        listOf(
            T(1, "Song", listOf("A", "B"), 200_000),
            T(2, "Song", listOf("A"), 200_000),
            T(3, "Song", listOf("A"), 200_500),
            T(4, "Other", listOf("Tyler, The Creator"), 100_000),
        )
    private val matcher = TrackMatcher(tracks, { it.title }, { it.artists }, { it.durationMs })

    @Test
    fun `find compares title artists and length`() {
        assertEquals(1, matcher.find("Song", listOf("A", "B"), 200_000)?.id)
        assertEquals(2, matcher.find("Song", listOf("A"), 200_000)?.id)
    }

    @Test
    fun `find ignores case spaces and the and sign`() {
        assertEquals(4, matcher.find("  OTHER ", listOf("tyler,  the creator"), 100_000)?.id)
    }

    @Test
    fun `find allows one second of difference and no more`() {
        assertEquals(2, matcher.find("Song", listOf("A"), 201_000)?.id)
        assertNull(matcher.find("Other", listOf("Tyler, The Creator"), 101_001))
    }

    @Test
    fun `find returns null for another artist order or title`() {
        assertNull(matcher.find("Song", listOf("B", "A"), 200_000))
        assertNull(matcher.find("Nope", listOf("A"), 200_000))
        assertNull(matcher.find("Song", emptyList(), 200_000))
    }

    @Test
    fun `findUnique refuses ambiguity`() {
        assertNull(matcher.findUnique("Song", 200_000))
        assertEquals(4, matcher.findUnique("Other", 100_000)?.id)
        assertNull(matcher.findUnique("Missing", 1))
    }
}
