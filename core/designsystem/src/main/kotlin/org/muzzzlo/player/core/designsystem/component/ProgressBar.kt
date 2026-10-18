package org.muzzzlo.player.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.designsystem.theme.MuzzzloTheme

/**
 * Playback progress: 8 high and rounded in the player (3:20), 2 high and flat in the mini player (15:54).
 *
 * @param progress played share in [0, 1].
 * @param onSeek when set, a tap or a horizontal drag seeks; the value is the target share in [0, 1].
 */
@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    thin: Boolean = false,
    onSeek: ((Float) -> Unit)? = null,
) {
    var dragShare by remember { mutableStateOf<Float?>(null) }
    val shown = (dragShare ?: progress).coerceIn(0f, 1f)
    val shape = if (thin) RectangleShape else MuzzzloDimens.PillShape
    val height = if (thin) MuzzzloDimens.ProgressThin else MuzzzloDimens.ProgressThick
    val seek by rememberUpdatedState(onSeek)
    val gestures =
        if (onSeek == null) {
            Modifier
        } else {
            Modifier
                .pointerInput(Unit) {
                    detectTapGestures { seek?.invoke(shareOf(it.x, size.width)) }
                }.pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragShare = shareOf(it.x, size.width) },
                        onDragEnd = {
                            dragShare?.let { share -> seek?.invoke(share) }
                            dragShare = null
                        },
                        onDragCancel = { dragShare = null },
                    ) { change, _ -> dragShare = shareOf(change.position.x, size.width) }
                }.padding(vertical = MuzzzloDimens.RowPadding)
        }
    Box(modifier.fillMaxWidth().then(gestures)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .clip(shape)
                .background(MuzzzloColors.Outline),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(shown)
                    .fillMaxHeight()
                    .clip(if (thin) RectangleShape else RoundedCornerShape(percent = 50))
                    .background(MuzzzloTheme.accent.accent),
            )
        }
    }
}

private fun shareOf(
    x: Float,
    width: Int,
): Float = if (width <= 0) 0f else (x / width).coerceIn(0f, 1f)
