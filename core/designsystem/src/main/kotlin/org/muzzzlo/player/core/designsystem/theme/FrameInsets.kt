package org.muzzzlo.player.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Paddings of the screen frame for one device (DESIGN.md, section 1). */
object FrameInsets {
    /** Screens narrower than this get the smaller paddings from the token set. */
    val CompactWidth = 360.dp

    private val CompactOuter = 16.dp
    private val CompactInner = 16.dp

    /** Space between the screen edge and the frame: the design value, or the system bar/cutout if it is larger. */
    fun outer(
        systemInset: Dp,
        screenWidth: Dp,
    ): Dp {
        val design = if (screenWidth < CompactWidth) CompactOuter else MuzzzloDimens.FrameOuterPadding
        return maxOf(design, systemInset)
    }

    /** Space between the frame border and its content. */
    fun inner(screenWidth: Dp): Dp = if (screenWidth < CompactWidth) CompactInner else MuzzzloDimens.FrameInnerPadding
}
