package org.muzzzlo.player.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import org.muzzzlo.player.core.designsystem.theme.GradientDirection
import org.muzzzlo.player.core.designsystem.theme.MuzzzloCoverGradients
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens

/**
 * The cover of a generated or smart playlist (28:620, task 2.5.7): a linear gradient of the pair at [index] in a
 * rounded square, without text. Pass the modifier the slot of [CoverTile] or [CoverCard] gives.
 */
@Composable
fun GradientCover(
    index: Int,
    modifier: Modifier = Modifier,
    shape: Shape = MuzzzloDimens.RowShape,
) {
    val pair = MuzzzloCoverGradients.pair(index)
    val colors = listOf(pair.start, pair.end)
    val brush =
        when (pair.direction) {
            GradientDirection.DIAGONAL -> Brush.linearGradient(colors, start = Offset.Zero, end = Offset.Infinite)
            GradientDirection.HORIZONTAL -> Brush.horizontalGradient(colors)
        }
    Box(modifier.background(brush, shape))
}
