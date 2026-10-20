package org.muzzzlo.player.core.designsystem.gesture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class SwipeMathTest {
    // Width 1000: the distance mark is 300 px, a fling is 1000 px/s.
    @ParameterizedTest(name = "offset {0}, speed {1}, prev={2}, next={3}: {4}")
    @CsvSource(
        "400, 0, true, true, PREVIOUS",
        "-400, 0, true, true, NEXT",
        "299, 0, true, true, NONE",
        "-299, 0, true, true, NONE",
        "300, 0, true, true, PREVIOUS",
        "50, 1500, true, true, PREVIOUS",
        "-50, -1500, true, true, NEXT",
        "50, 999, true, true, NONE",
        "50, -1500, true, true, NONE",
        "-50, 1500, true, true, NONE",
        "0, 5000, true, true, NONE",
        "400, 0, false, true, NONE",
        "-400, 0, true, false, NONE",
        "-400, 0, false, true, NEXT",
        "400, 0, true, false, PREVIOUS",
    )
    fun `skip result`(
        offset: Float,
        speed: Float,
        canPrevious: Boolean,
        canNext: Boolean,
        expected: SwipeSkip,
    ) = assertEquals(expected, skipResult(offset, speed, 1000f, canPrevious, canNext))

    @ParameterizedTest(name = "unknown width {0} never skips")
    @CsvSource("0", "-5")
    fun `no width means no skip`(width: Float) =
        assertEquals(SwipeSkip.NONE, skipResult(500f, 5000f, width, canPrevious = true, canNext = true))

    @ParameterizedTest(name = "raw {0}, prev={1}, next={2}: {3}")
    @CsvSource(
        "100, true, true, 100",
        "-100, true, true, -100",
        "100, false, true, 25",
        "-100, true, false, -25",
        "100, true, false, 100",
        "0, false, false, 0",
    )
    fun `damped offset`(
        raw: Float,
        canPrevious: Boolean,
        canNext: Boolean,
        expected: Float,
    ) = assertEquals(expected, dampedOffset(raw, canPrevious, canNext))

    @ParameterizedTest(name = "progress {0}, speed {1}: open={2}")
    @CsvSource(
        "0.5, 0, true",
        "0.49, 0, false",
        "0.9, 0, true",
        "0.1, 0, false",
        "0.1, -2000, true",
        "0.9, 2000, false",
        "0.9, 299, true",
        "0.1, -299, false",
        "0.9, 300, false",
        "0.1, -300, true",
    )
    fun `sheet settles`(
        progress: Float,
        speed: Float,
        expected: Boolean,
    ) = if (expected) {
        assertTrue(sheetSettlesOpen(progress, speed, 1000f))
    } else {
        assertFalse(sheetSettlesOpen(progress, speed, 1000f))
    }
}
