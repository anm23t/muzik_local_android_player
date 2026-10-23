package org.muzzzlo.player.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.designsystem.theme.MuzzzloTheme
import kotlin.math.abs

/**
 * One band of the equalizer (task 1.2.8, no mockup): the value above, a vertical track as thick as the player
 * progress bar (3:20) with a thin middle line at 0, the accent fill from the middle to the value, and the frequency
 * below. A tap or a vertical drag sets the value; the caller rounds it to its step.
 *
 * @param value current gain in [min, max].
 * @param onChange new gain in [min, max] while the finger moves.
 */
@Composable
fun BandSlider(
    value: Float,
    min: Float,
    max: Float,
    valueLabel: String,
    frequencyLabel: String,
    description: String,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val change by rememberUpdatedState(onChange)
    val accent = MuzzzloTheme.accent.accent
    Column(
        modifier.semantics {
            contentDescription = description
            stateDescription = valueLabel
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.SmallGap),
    ) {
        BandLabel(valueLabel, MuzzzloColors.TextPrimary)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(MuzzzloDimens.BandSliderHeight)
                .pointerInput(min, max) {
                    detectTapGestures { change(bandValueAt(it.y, size.height.toFloat(), min, max)) }
                }.pointerInput(min, max) {
                    detectVerticalDragGestures { pointer, _ ->
                        pointer.consume()
                        change(bandValueAt(pointer.position.y, size.height.toFloat(), min, max))
                    }
                },
        ) { drawBand(value, min, max, accent) }
        BandLabel(frequencyLabel, MuzzzloColors.TextSecondary)
    }
}

@Composable
private fun BandLabel(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 1,
        textAlign = TextAlign.Center,
    )
}

private fun DrawScope.drawBand(
    value: Float,
    min: Float,
    max: Float,
    accent: Color,
) {
    val track = MuzzzloDimens.ProgressThick.toPx()
    val left = (size.width - track) / 2
    val radius = CornerRadius(track / 2, track / 2)
    drawRoundRect(MuzzzloColors.Outline, Offset(left, 0f), Size(track, size.height), radius)
    val middle = bandYOf(0f, size.height, min, max)
    val at = bandYOf(value, size.height, min, max)
    val top = minOf(middle, at)
    // A flat band still shows a dot of the accent at the middle.
    val height = maxOf(abs(at - middle), track)
    drawRoundRect(accent, Offset(left, minOf(top, size.height - height)), Size(track, height), radius)
    val line = MuzzzloDimens.BorderWidth.toPx()
    drawRect(MuzzzloColors.TextSecondary, Offset(left - track / 2, middle - line / 2), Size(track * 2, line))
}

/** The value at [y] pixels from the top of a track [height] tall: [max] at the top, [min] at the bottom. */
internal fun bandValueAt(
    y: Float,
    height: Float,
    min: Float,
    max: Float,
): Float {
    if (height <= 0f) return 0f.coerceIn(min, max)
    val share = (1f - y / height).coerceIn(0f, 1f)
    return min + share * (max - min)
}

/** Where [value] sits on a track [height] tall, inverse of [bandValueAt]. */
internal fun bandYOf(
    value: Float,
    height: Float,
    min: Float,
    max: Float,
): Float = if (max <= min) height / 2 else (1f - (value.coerceIn(min, max) - min) / (max - min)) * height
