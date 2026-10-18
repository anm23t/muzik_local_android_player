package org.muzzzlo.player.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens

/**
 * The rounded frame around every screen (DESIGN.md, section 1; mockup node 15:4).
 * Adapting the outer padding to the device is task 1.0.2.
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
    Box(
        modifier
            .fillMaxSize()
            .background(MuzzzloColors.Background)
            .padding(MuzzzloDimens.FrameOuterPadding),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(MuzzzloDimens.FrameShape)
                .background(MuzzzloColors.Background)
                .border(
                    MuzzzloDimens.BorderWidth,
                    if (outlined) MuzzzloColors.Outline else MuzzzloColors.Background,
                    MuzzzloDimens.FrameShape,
                )
                .padding(MuzzzloDimens.FrameInnerPadding),
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
