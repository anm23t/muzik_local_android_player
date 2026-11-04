package org.muzzzlo.player.core.designsystem.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.hypot

class BlobMotionTest {
    @Test
    fun `one spot per color, at most four`() {
        assertEquals(0, BlobMotion.blobs(0, 1f).size)
        assertEquals(3, BlobMotion.blobs(3, 1f).size)
        assertEquals(4, BlobMotion.blobs(9, 1f).size)
        assertEquals(0, BlobMotion.blobs(-1, 1f).size)
    }

    @Test
    fun `the same moment gives the same picture`() {
        assertEquals(BlobMotion.blobs(4, 12.5f), BlobMotion.blobs(4, 12.5f))
    }

    @Test
    fun `spots move slowly and smoothly`() {
        // A frame at 60 fps moves a spot by a tiny share of the screen.
        val before = BlobMotion.blobs(4, 100f)
        val after = BlobMotion.blobs(4, 100f + 1f / 60)
        before.zip(after).forEach { (a, b) ->
            assertTrue(hypot(a.x - b.x, a.y - b.y) < 0.003f)
            assertTrue(abs(a.radius - b.radius) < 0.003f)
        }
        assertNotEquals(BlobMotion.blobs(4, 0f), BlobMotion.blobs(4, 5f))
    }

    @Test
    fun `spots stay around the screen and keep a soft size`() {
        for (second in 0..600 step 7) {
            BlobMotion.blobs(4, second.toFloat()).forEach {
                assertTrue(it.x in 0f..1f, "x ${it.x} at $second")
                assertTrue(it.y in 0f..1f, "y ${it.y} at $second")
                assertTrue(it.radius in 0.45f..0.65f, "radius ${it.radius} at $second")
            }
        }
    }
}
