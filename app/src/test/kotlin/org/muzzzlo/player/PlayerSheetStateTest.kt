package org.muzzzlo.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PlayerSheetStateTest {
    private fun sheet() = PlayerSheetState(CoroutineScope(Dispatchers.Unconfined)).apply { heightPx = 1000f }

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
}
