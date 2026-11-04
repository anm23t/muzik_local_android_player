package org.muzzzlo.player.core.designsystem.gesture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class DragMathTest {
    @ParameterizedTest
    @CsvSource(
        "2, 0, 100, 5, 2",
        "2, 49, 100, 5, 2",
        "2, 51, 100, 5, 3",
        "2, -160, 100, 5, 0",
        "2, -1000, 100, 5, 0",
        "2, 1000, 100, 5, 4",
        "0, 100, 0, 5, 0",
        "0, 100, 100, 0, 0",
    )
    fun `target index follows the drag distance and stays in range`(
        start: Int,
        offset: Float,
        rowHeight: Float,
        size: Int,
        expected: Int,
    ) {
        assertEquals(expected, dragTargetIndex(start, offset, rowHeight, size))
    }
}

internal class NeighborShiftTest {
    @ParameterizedTest(name = "row {0}, held {1} over {2} -> {3}")
    @CsvSource(
        // Dragged down from 1 to 3: rows 2 and 3 move up a step.
        "0, 1, 3, 0",
        "1, 1, 3, 0",
        "2, 1, 3, -100",
        "3, 1, 3, -100",
        "4, 1, 3, 0",
        // Dragged up from 4 to 1: rows 1 to 3 move down a step.
        "0, 4, 1, 0",
        "1, 4, 1, 100",
        "3, 4, 1, 100",
        "4, 4, 1, 0",
        "5, 4, 1, 0",
        // Held over its own place, or nothing held.
        "2, 2, 2, 0",
        "3, 2, 2, 0",
        "3, -1, 2, 0",
    )
    fun `rows between the old and the new place step aside`(
        index: Int,
        from: Int,
        target: Int,
        expected: Float,
    ) {
        assertEquals(expected, neighborShift(index, from, target, 100f))
    }
}

internal class AutoScrollStepTest {
    // A list from 0 to 1000 px, edge zones of 100 px, at most 20 px a frame.
    private fun step(
        top: Float,
        bottom: Float,
    ) = autoScrollStep(top, bottom, 0f, 1000f, 100f, 20f)

    @ParameterizedTest(name = "row {0}..{1} -> {2}")
    @CsvSource(
        "400, 460, 0",
        "100, 160, 0",
        "50, 110, -10",
        "0, 60, -20",
        "-80, -20, -20",
        "840, 900, 0",
        "900, 950, 10",
        "940, 1000, 20",
        "1100, 1160, 20",
    )
    fun `the list scrolls faster the closer the row is to an edge`(
        top: Float,
        bottom: Float,
        expected: Float,
    ) {
        assertEquals(expected, step(top, bottom), 0.001f)
    }

    @org.junit.jupiter.api.Test
    fun `a list too short for two edge zones never scrolls`() {
        assertEquals(0f, autoScrollStep(0f, 50f, 0f, 150f, 100f, 20f))
        assertEquals(0f, autoScrollStep(0f, 50f, 0f, 1000f, 0f, 20f))
    }
}

internal class StableKeysTest {
    @org.junit.jupiter.api.Test
    fun `repeated items get their own keys that survive a move`() {
        assertEquals(listOf("7#0", "3#0", "7#1"), stableKeys(listOf(7, 3, 7)) { it })
        // The first 7 moved to the end: the other rows keep their keys.
        assertEquals(listOf("3#0", "7#0", "7#1"), stableKeys(listOf(3, 7, 7)) { it })
        assertEquals(emptyList<String>(), stableKeys(emptyList<Int>()) { it })
    }
}
