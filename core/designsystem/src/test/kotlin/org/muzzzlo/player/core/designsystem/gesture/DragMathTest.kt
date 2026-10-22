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
