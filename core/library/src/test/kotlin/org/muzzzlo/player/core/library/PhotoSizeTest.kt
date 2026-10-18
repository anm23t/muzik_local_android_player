package org.muzzzlo.player.core.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class PhotoSizeTest {
    @ParameterizedTest(name = "{0}x{1} to {2}: sample {3}")
    @CsvSource(
        // Already small: decode as is.
        "100, 100, 512, 1",
        "512, 300, 512, 1",
        // Just under twice the target: halving would go below it.
        "1023, 10, 512, 1",
        "1024, 768, 512, 2",
        // A 12 MP camera photo, both orientations.
        "4000, 3000, 512, 4",
        "3000, 4000, 512, 4",
        "4096, 4096, 512, 8",
    )
    fun `the sample size keeps the longer side at least the target`(
        width: Int,
        height: Int,
        target: Int,
        expected: Int,
    ) {
        assertEquals(expected, sampleSizeFor(width, height, target))
    }

    @ParameterizedTest(name = "{0}x{1} max {2}: {3}x{4}")
    @CsvSource(
        "300, 200, 512, 300, 200",
        "512, 512, 512, 512, 512",
        "1000, 500, 512, 512, 256",
        "500, 1000, 512, 256, 512",
        "1000, 333, 512, 512, 170",
        // A very thin picture never shrinks to zero.
        "5000, 1, 512, 512, 1",
    )
    fun `the photo is reduced to the longer side and keeps its proportions`(
        width: Int,
        height: Int,
        maxSide: Int,
        expectedWidth: Int,
        expectedHeight: Int,
    ) {
        assertEquals(expectedWidth to expectedHeight, scaledSize(width, height, maxSide))
    }
}
