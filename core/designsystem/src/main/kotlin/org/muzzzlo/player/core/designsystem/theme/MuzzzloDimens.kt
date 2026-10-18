package org.muzzzlo.player.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Sizes, paddings and corner radii taken from the mockup (DESIGN.md, section 2). */
object MuzzzloDimens {
    // Corner radii.
    val FrameRadius = 32.dp
    val CardRadius = 24.dp
    val MiniPlayerRadius = 16.dp
    val InnerCoverRadius = 10.dp
    val RowRadius = 8.dp

    // Paddings and gaps.
    val FrameOuterPadding = 24.dp
    val FrameInnerPadding = 24.dp
    val DialogPadding = 20.dp
    val DialogMargin = 40.dp
    val DialogMaxWidth = 332.dp
    val RowPadding = 8.dp
    val RowEndPadding = 4.dp
    val DialogRowHorizontalPadding = 12.dp
    val SectionGap = 16.dp
    val ContentGap = 12.dp
    val RowGap = 16.dp
    val ItemGap = 10.dp
    val SmallGap = 6.dp
    val TinyGap = 2.dp

    // Element sizes.
    val TrackRowMinHeight = 40.dp
    val DialogRowHeight = 40.dp
    val MiniPlayerHeight = 64.dp
    val MiniPlayerCover = 40.dp
    val ListCover = 70.dp
    val ArtistPhoto = 50.dp
    val TrackNumberWidth = 20.dp
    val PillHeight = 32.dp
    val WidePillHeight = 40.dp
    val RadioSize = 18.dp
    val RadioDot = 8.dp
    val RadioStroke = 1.5.dp
    val ProgressThick = 8.dp
    val ProgressThin = 2.dp
    val BorderWidth = 1.dp
    val PrimaryButtonGlow = 12.dp
    val DialogShadowBlur = 48.dp
    val SmallIcon = 14.dp

    // Round icon buttons and their icons.
    val ButtonSmall = 32.dp
    val ButtonCompact = 36.dp
    val ButtonMedium = 44.dp
    val ButtonLarge = 48.dp
    val ButtonControl = 56.dp
    val ButtonPrimary = 72.dp
    val IconCompact = 16.dp
    val IconMedium = 20.dp
    val IconControl = 24.dp
    val IconPrimary = 28.dp

    // Now playing indicator (mockup node 4:364).
    val LevelBarWidth = 2.dp
    val LevelBarRadius = 1.dp
    val LevelBarHeights = listOf(7.dp, 15.dp, 10.dp)

    val PillShape = RoundedCornerShape(percent = 50)
    val RowShape = RoundedCornerShape(RowRadius)
    val DialogShape = RoundedCornerShape(CardRadius)
    val FrameShape = RoundedCornerShape(FrameRadius)
    val MiniPlayerShape = RoundedCornerShape(MiniPlayerRadius)
}
