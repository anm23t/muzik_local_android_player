package org.muzzzlo.player.core.designsystem.theme

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FrameInsetsTest {
    @Test
    fun `normal width without bars keeps the design padding`() = assertEquals(24.dp, FrameInsets.outer(0.dp, 411.dp))

    @Test
    fun `a small bar does not shrink the design padding`() = assertEquals(24.dp, FrameInsets.outer(20.dp, 411.dp))

    @Test
    fun `a tall status bar or cutout pushes the frame`() = assertEquals(48.dp, FrameInsets.outer(48.dp, 411.dp))

    @Test
    fun `narrow screen uses the smaller padding`() = assertEquals(16.dp, FrameInsets.outer(0.dp, 320.dp))

    @Test
    fun `narrow screen still respects a larger inset`() = assertEquals(30.dp, FrameInsets.outer(30.dp, 320.dp))

    @Test
    fun `width exactly at the limit is not compact`() = assertEquals(24.dp, FrameInsets.outer(0.dp, 360.dp))

    @Test
    fun `inner padding follows the width`() {
        assertEquals(24.dp, FrameInsets.inner(411.dp))
        assertEquals(16.dp, FrameInsets.inner(359.dp))
    }

    @Test
    fun `huge style has no frame padding, only the system inset`() {
        assertEquals(0.dp, FrameInsets.outer(0.dp, 411.dp, FrameStyle.Huge))
        assertEquals(30.dp, FrameInsets.outer(30.dp, 320.dp, FrameStyle.Huge))
    }

    @Test
    fun `huge style keeps content 12 dp from the edges on any width`() {
        assertEquals(12.dp, FrameInsets.inner(411.dp, FrameStyle.Huge))
        assertEquals(12.dp, FrameInsets.inner(320.dp, FrameStyle.Huge))
    }
}
