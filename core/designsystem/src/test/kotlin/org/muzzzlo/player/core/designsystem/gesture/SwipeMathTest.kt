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

    // Height 1000: the fling mark is 300 px/s; the settle mark is 25% of the path from where the drag began.
    @ParameterizedTest(name = "from {0} to {1}, speed {2}: open={3}")
    @CsvSource(
        // Opening from closed.
        "0, 0.25, 0, true",
        "0, 0.24, 0, false",
        "0, 0.5, 0, true",
        "0, 0.1, 0, false",
        "0, 0.1, -300, true",
        "0, 0.9, 300, false",
        "0, 0.1, -299, false",
        "0, 0.9, 299, true",
        // Closing from open.
        "1, 0.75, 0, false",
        "1, 0.76, 0, true",
        "1, 0.5, 0, false",
        "1, 0.9, 0, true",
        "1, 0.9, 300, false",
        "1, 0.1, -300, true",
        "1, 0.9, 299, true",
        "1, 0.6, -299, false",
        // A drag that caught the sheet mid-animation counts from the nearer end.
        "0.4, 0.25, 0, true",
        "0.6, 0.75, 0, false",
    )
    fun `sheet settles`(
        start: Float,
        progress: Float,
        speed: Float,
        expected: Boolean,
    ) = if (expected) {
        assertTrue(sheetSettlesOpen(progress, speed, 1000f, start))
    } else {
        assertFalse(sheetSettlesOpen(progress, speed, 1000f, start))
    }

    @ParameterizedTest(name = "unknown height {0}: the fling is ignored")
    @CsvSource("0", "-5")
    fun `no height means no fling`(height: Float) =
        assertFalse(sheetSettlesOpen(0.1f, -5000f, height, startProgress = 0f))
}
