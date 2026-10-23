package org.muzzzlo.player.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How screens are framed (task 1.2.6, the "Theme" setting). The one switch of the design system for it: the
 * screen frame, outlined panels and cover cards read it from [LocalFrameStyle].
 */
enum class FrameStyle {
    /** The rounded window with borders of the mockup (default). */
    Window,

    /** No frame and no inner borders: content 12 dp from the screen edges, covers rounded without a card. */
    Huge,
}

/** Frame style of the composition. Provided by [MuzzzloTheme]; it changes only from the settings. */
val LocalFrameStyle = staticCompositionLocalOf { FrameStyle.Window }
