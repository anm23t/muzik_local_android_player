package org.muzzzlo.player

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PlayerSheetStateTest {
    /** Animations run to the end at once: frames come back immediately, 16 ms apart. */
    private val instantFrames =
        object : MonotonicFrameClock {
            private var nanos = 0L

            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                nanos += FRAME_NANOS
                return onFrame(nanos)
            }
        }

    private fun sheet() =
        PlayerSheetState(CoroutineScope(Dispatchers.Unconfined + instantFrames)).apply { heightPx = 1000f }

    @Test
    fun `dragging up opens the sheet by the share of the height`() {
        val sheet = sheet()
        sheet.dragBy(-250f)
        assertEquals(0.25f, sheet.progress)
    }

    @Test
    fun `dragging past closed or open is clamped`() {
        val sheet = sheet()
        sheet.dragBy(500f)
        assertEquals(0f, sheet.progress)
        sheet.dragBy(-2000f)
        assertEquals(1f, sheet.progress)
    }

    @Test
    fun `drag without a known height does nothing`() {
        val sheet = PlayerSheetState(CoroutineScope(Dispatchers.Unconfined))
        sheet.dragBy(-100f)
        assertEquals(0f, sheet.progress)
    }

    @Test
    fun `hide closes at once`() {
        val sheet = sheet()
        sheet.moveTo(1f)
        sheet.hide()
        assertEquals(0f, sheet.progress)
    }

    @Test
    fun `a quarter of the path up opens the sheet`() {
        val sheet = sheet()
        sheet.dragBy(-250f)
        sheet.release(0f)
        assertEquals(1f, sheet.progress)
    }

    @Test
    fun `less than a quarter up goes back closed`() {
        val sheet = sheet()
        sheet.dragBy(-240f)
        sheet.release(0f)
        assertEquals(0f, sheet.progress)
    }

    @Test
    fun `a quarter of the path down closes an open sheet`() {
        val sheet = sheet()
        sheet.moveTo(1f)
        sheet.dragBy(150f)
        sheet.dragBy(100f)
        sheet.release(0f)
        assertEquals(0f, sheet.progress)
    }

    @Test
    fun `less than a quarter down keeps the sheet open`() {
        val sheet = sheet()
        sheet.moveTo(1f)
        sheet.dragBy(240f)
        sheet.release(0f)
        assertEquals(1f, sheet.progress)
    }

    @Test
    fun `each drag counts from its own start`() {
        val sheet = sheet()
        sheet.dragBy(-300f)
        sheet.release(0f)
        // Open now; the next drag is a closing one even though it ends above a quarter.
        sheet.dragBy(300f)
        sheet.release(0f)
        assertEquals(0f, sheet.progress)
    }

    private companion object {
        const val FRAME_NANOS = 16_000_000L
    }
}
