package org.muzzzlo.player.core.designsystem.theme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CoverGradientsTest {
    @Test
    fun `there are as many pairs as core model counts`() {
        // CoverGradients.COUNT in core:model; this module does not depend on it.
        assertEquals(12, MuzzzloCoverGradients.pairs.size)
    }

    @Test
    fun `pairs are distinct and both directions are used`() {
        val pairs = MuzzzloCoverGradients.pairs
        assertEquals(pairs.size, pairs.map { it.start to it.end }.distinct().size)
        assertEquals(GradientDirection.entries.toSet(), pairs.map { it.direction }.toSet())
    }

    @Test
    fun `an index out of range wraps`() {
        val pairs = MuzzzloCoverGradients.pairs
        assertEquals(pairs[0], MuzzzloCoverGradients.pair(pairs.size))
        assertEquals(pairs.last(), MuzzzloCoverGradients.pair(-1))
    }
}
