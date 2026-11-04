package org.muzzzlo.player.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * One soft spot of the live background at a moment: the centre in shares of the width and height and the radius in
 * shares of the longer side.
 */
data class Blob(
    val x: Float,
    val y: Float,
    val radius: Float,
)

/**
 * How the spots of the live background float (task 2.0.8): every spot drifts along its own slow figure and breathes
 * in size, as a pure function of time, so a frozen moment (reduced motion, the app in the background) is just one
 * value of it and two layers drawn at the same time look the same.
 */
object BlobMotion {
    /** Most spots drawn; a palette has 3 or 4 colors. */
    const val MAX_BLOBS = 4

    // Each spot has its own periods, so the figure never repeats in a way the eye notices.
    // Periods of 10-25 s: slow enough to stay calm, fast enough that the motion is seen (owner's remark).
    private val PERIODS_X = floatArrayOf(17f, 23f, 19f, 25f)
    private val PERIODS_Y = floatArrayOf(21f, 15f, 24f, 18f)
    private val PERIODS_SIZE = floatArrayOf(11f, 13f, 10f, 14f)
    private val CENTRES_X = floatArrayOf(0.25f, 0.75f, 0.35f, 0.7f)
    private val CENTRES_Y = floatArrayOf(0.25f, 0.35f, 0.75f, 0.7f)
    private val PHASES = floatArrayOf(0f, 1.7f, 3.1f, 4.6f)
    private const val DRIFT = 0.25f
    private const val BASE_RADIUS = 0.55f
    private const val BREATH = 0.15f
    private const val TWO_PI = (2 * PI).toFloat()

    /** The spots at [timeSec]; [count] is clamped to 0..[MAX_BLOBS]. */
    fun blobs(
        count: Int,
        timeSec: Float,
    ): List<Blob> =
        List(count.coerceIn(0, MAX_BLOBS)) { i ->
            Blob(
                x = CENTRES_X[i] + DRIFT * wave(timeSec, PERIODS_X[i], PHASES[i]),
                y = CENTRES_Y[i] + DRIFT * wave(timeSec, PERIODS_Y[i], PHASES[i] * 2),
                radius = BASE_RADIUS * (1f + BREATH * wave(timeSec, PERIODS_SIZE[i], PHASES[i])),
            )
        }

    private fun wave(
        timeSec: Float,
        periodSec: Float,
        phase: Float,
    ): Float = sin(TWO_PI * timeSec / periodSec + phase)
}

/**
 * True while the app draws the live background under its screens: [ScreenFrame] is then transparent so it shows
 * through. Provided by the app module.
 */
val LocalLiveBackground = staticCompositionLocalOf { false }

/**
 * The live background (task 2.0.8, no mockup, DESIGN.md, section 7): large soft spots in [colors] float on black
 * under a black veil of [dim] (0 none, 1 black). [timeSec] is read in the draw phase only, so a running clock redraws
 * without recomposing; [colorAmount] (0 black, 1 colored) fades the spots in and out. Radial gradients on a canvas,
 * without blur or shaders, so it works on every supported Android.
 */
@Composable
fun LiveBackground(
    colors: List<Color>,
    timeSec: () -> Float,
    colorAmount: () -> Float,
    dim: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        drawRect(MuzzzloColors.Background)
        val amount = colorAmount().coerceIn(0f, 1f)
        if (amount > 0f && colors.isNotEmpty()) {
            val longer = max(size.width, size.height)
            BlobMotion.blobs(colors.size, timeSec()).forEachIndexed { i, blob ->
                val centre = Offset(blob.x * size.width, blob.y * size.height)
                val radius = blob.radius * longer
                val color = colors[i]
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            colors = listOf(color.copy(alpha = color.alpha * amount), Color.Transparent),
                            center = centre,
                            radius = radius,
                        ),
                    radius = radius,
                    center = centre,
                )
            }
        }
        drawRect(MuzzzloColors.Background.copy(alpha = dim.coerceIn(0f, 1f)))
    }
}
