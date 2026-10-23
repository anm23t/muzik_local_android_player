package org.muzzzlo.player.core.designsystem.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.designsystem.theme.MuzzzloTheme
import kotlin.math.floor

/** Duration of the change from the plain bar to the waveform, once the peaks are ready. */
const val WAVEFORM_FADE_MS = 300

/**
 * Progress of the big player as a waveform of bars, like SoundCloud (task 1.2.7, no mockup): played bars in the
 * accent, the rest in [MuzzzloColors.WaveformRest]. A tap or a horizontal drag seeks, as on [ProgressBar].
 * Until [levels] is given the plain [ProgressBar] is shown at the same height, then the waveform fades in.
 *
 * @param levels bar heights in (0, 1] for the given number of bars; computed by the caller, which has the peaks.
 * @param onSeek target share in [0, 1]; null disables seeking.
 */
@Composable
fun WaveformBar(
    progress: Float,
    levels: ((barCount: Int) -> FloatArray)?,
    modifier: Modifier = Modifier,
    onSeek: ((Float) -> Unit)? = null,
) {
    Crossfade(
        targetState = levels,
        modifier = modifier.fillMaxWidth().height(MuzzzloDimens.WaveformHeight),
        animationSpec = tween(WAVEFORM_FADE_MS),
        label = "waveform",
    ) { shown ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (shown == null) ProgressBar(progress, onSeek = onSeek) else Bars(progress, shown, onSeek)
        }
    }
}

@Composable
private fun Bars(
    progress: Float,
    levels: (Int) -> FloatArray,
    onSeek: ((Float) -> Unit)?,
) {
    var widthPx by remember { mutableIntStateOf(0) }
    var dragShare by remember { mutableStateOf<Float?>(null) }
    val density = LocalDensity.current
    val barPx = with(density) { MuzzzloDimens.WaveformBarWidth.toPx() }
    val gapPx = with(density) { MuzzzloDimens.WaveformBarGap.toPx() }
    val count = waveformBarCount(widthPx.toFloat(), barPx, gapPx)
    val heights = remember(levels, count) { levels(count) }
    val played = MuzzzloTheme.accent.accent
    val shown = (dragShare ?: progress).coerceIn(0f, 1f)
    val input = if (onSeek == null) Modifier else Modifier.seekInput(onSeek) { dragShare = it }
    Canvas(Modifier.fillMaxSize().onSizeChanged { widthPx = it.width }.then(input)) {
        drawBars(heights, barPx, gapPx, shown, played)
    }
}

private fun DrawScope.drawBars(
    heights: FloatArray,
    barPx: Float,
    gapPx: Float,
    progress: Float,
    played: Color,
) {
    val playedUntil = progress * size.width
    val radius = CornerRadius(barPx / 2, barPx / 2)
    heights.forEachIndexed { i, level ->
        val x = i * (barPx + gapPx)
        val h = (level.coerceIn(0f, 1f) * size.height).coerceAtLeast(barPx)
        val color = if (x + barPx / 2 <= playedUntil) played else MuzzzloColors.WaveformRest
        drawRoundRect(color, Offset(x, (size.height - h) / 2), Size(barPx, h), radius)
    }
}

/** Bars of [barWidth] with [gap] between them that fit [width] (all in pixels). */
internal fun waveformBarCount(
    width: Float,
    barWidth: Float,
    gap: Float,
): Int = if (barWidth <= 0f || width < barWidth) 0 else floor((width + gap) / (barWidth + gap)).toInt()
