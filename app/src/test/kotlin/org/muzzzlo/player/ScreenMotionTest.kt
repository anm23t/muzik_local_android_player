package org.muzzzlo.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class ScreenMotionTest {
    @ParameterizedTest(name = "{0} -> {1}, pop={2}: {3}")
    @CsvSource(
        nullValues = ["null"],
        value = [
            // Ordinary pages in both directions.
            "home, albums, false, PAGE",
            "albums, home, true, PAGE",
            "album/{albumId}, tagEditorAlbum/{albumId}, false, PAGE",
            // The player rises over any screen and goes back down.
            "home, player, false, PLAYER",
            "album/{albumId}, player, false, PLAYER",
            "player, home, true, PLAYER",
            "player, album/{albumId}, true, PLAYER",
            // Screens opened from the player are pages, also on the way back to it.
            "player, queue, false, PAGE",
            "queue, player, true, PAGE",
            "player, tagEditor/{trackId}, false, PAGE",
            // A pop that lands on the player does not move it vertically.
            "tagEditor/{trackId}, player, true, PAGE",
            // A route string that only contains the player route is another screen.
            "home, playerSettings, false, PAGE",
            // No known route (start of the graph) falls back to a page.
            "null, home, false, PAGE",
            "home, null, true, PAGE",
        ],
    )
    fun `picks the motion`(
        from: String?,
        to: String?,
        isPop: Boolean,
        expected: ScreenMotion,
    ) = assertEquals(expected, screenMotion(from, to, isPop, ROUTE_PLAYER))
}
