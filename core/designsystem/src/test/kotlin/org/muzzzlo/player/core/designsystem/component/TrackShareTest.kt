package org.muzzzlo.player.core.designsystem.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TrackShareTest {
    private val a = "content://media/external/audio/media/1"
    private val b = "content://media/external/audio/media/2"

    @Test
    fun `content addresses keep their order`() = assertEquals(listOf(b, a), TrackShare.shareable(listOf(b, a)))

    @Test
    fun `repeats blanks and addresses that are not content are dropped`() {
        val input = listOf(a, " ", "", "file:///sdcard/Music/x.mp3", "/sdcard/Music/y.mp3", "content://", a, b)
        assertEquals(listOf(a, b), TrackShare.shareable(input))
    }

    @Test
    fun `surrounding spaces are trimmed before the check`() =
        assertEquals(
            listOf(a),
            TrackShare.shareable(listOf("  $a ")),
        )

    @Test
    fun `nothing to share gives an empty list`() = assertEquals(emptyList<String>(), TrackShare.shareable(emptyList()))
}
