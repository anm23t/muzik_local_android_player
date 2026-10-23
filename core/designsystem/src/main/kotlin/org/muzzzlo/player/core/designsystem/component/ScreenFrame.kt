package org.muzzzlo.player.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.muzzzlo.player.core.designsystem.theme.FrameInsets
import org.muzzzlo.player.core.designsystem.theme.FrameStyle
import org.muzzzlo.player.core.designsystem.theme.LocalFrameStyle
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens

/**
 * The rounded frame around every screen (DESIGN.md, section 1; mockup node 15:4).
 * The outer padding adapts to the device: system bars, cutouts and the keyboard push the frame in,
 * narrow screens use smaller paddings ([FrameInsets]).
 * The frame reads the raw window insets, so parents must not pad for them again: the keyboard would count twice.
 * In [FrameStyle.Huge] there is no frame: the content keeps [FrameInsets.HugeInner] from the screen edges.
 *
 * @param outlined draws the border; the home screen (28:620) keeps the paddings of the frame without it.
 */
@Composable
fun ScreenFrame(
    modifier: Modifier = Modifier,
    spacing: Dp = MuzzzloDimens.SectionGap,
    outlined: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val width = LocalConfiguration.current.screenWidthDp.dp
    val density = LocalDensity.current
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout).union(WindowInsets.ime)
    val style = LocalFrameStyle.current
    val border = outlined && style == FrameStyle.Window

    fun side(px: Int) = FrameInsets.outer(with(density) { px.toDp() }, width, style)
    Box(
        modifier
            .fillMaxSize()
            .background(MuzzzloColors.Background)
            .padding(
                start = side(insets.getLeft(density, LocalLayoutDirection.current)),
                top = side(insets.getTop(density)),
                end = side(insets.getRight(density, LocalLayoutDirection.current)),
                bottom = side(insets.getBottom(density)),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(MuzzzloDimens.FrameShape)
                .background(MuzzzloColors.Background)
                .border(
                    MuzzzloDimens.BorderWidth,
                    if (border) MuzzzloColors.Outline else MuzzzloColors.Background,
                    MuzzzloDimens.FrameShape,
                )
                .padding(FrameInsets.inner(width, style)),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/**
 * Screen section title such as "Albums" (15:13).
 *
 * @param maxLines long titles of the home screen ("Продолжить прослушивание?") wrap instead of being cut.
 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.headlineSmall,
        color = MuzzzloColors.TextPrimary,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
