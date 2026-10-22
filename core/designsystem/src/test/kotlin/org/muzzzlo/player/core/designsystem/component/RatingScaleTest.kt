package org.muzzzlo.player.core.designsystem.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RatingScaleTest {
    @Test
    fun `format shows one decimal with a point`() {
        val cases = mapOf(0 to "0.0", 1 to "0.1", 9 to "0.9", 10 to "1.0", 67 to "6.7", 99 to "9.9", 100 to "10.0")
        cases.forEach { (tenths, text) -> assertEquals(text, RatingScale.format(tenths), "format($tenths)") }
    }

    @Test
    fun `format keeps out of range values inside the scale`() {
        assertEquals("0.0", RatingScale.format(-5))
        assertEquals("10.0", RatingScale.format(250))
    }

    @Test
    fun `a position on the bar becomes tenths`() {
        val cases = mapOf(0f to 0, 0.5f to 50, 0.674f to 67, 0.675f to 68, 1f to 100, -0.3f to 0, 1.7f to 100)
        cases.forEach { (share, tenths) -> assertEquals(tenths, RatingScale.fromShare(share), "fromShare($share)") }
    }

    @Test
    fun `tenths and bar position convert back`() {
        (RatingScale.MIN..RatingScale.MAX).forEach { assertEquals(it, RatingScale.fromShare(RatingScale.toShare(it))) }
    }

    @Test
    fun `step stops at both ends`() {
        assertEquals(68, RatingScale.step(67, 1))
        assertEquals(66, RatingScale.step(67, -1))
        assertEquals(0, RatingScale.step(0, -1))
        assertEquals(100, RatingScale.step(100, 1))
    }
}
