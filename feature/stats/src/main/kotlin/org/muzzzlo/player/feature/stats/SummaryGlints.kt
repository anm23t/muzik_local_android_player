package org.muzzzlo.player.feature.stats

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

private const val GLINTS = 18
private const val GRAIN_POINTS = 700
private const val SEED = 2512
private const val RISE_PERIOD_SEC = 40f
private const val TWINKLE_MIN_SEC = 3f
private const val TWINKLE_SPREAD_SEC = 4f
private const val GLINT_MAX_ALPHA = 0.55f
private const val GLINT_MIN_RADIUS = 0.006f
private const val GLINT_RADIUS_SPREAD = 0.018f
private const val GRAIN_ALPHA = 0.06f
private const val GRAIN_STROKE = 1.5f

/** A sine moved to 0..1. */
private const val HALF = 0.5f
private const val TWO_PI = (2 * PI).toFloat()

/** One glint: where it starts in shares of the slide, how big it is and how fast it twinkles. */
private class Glint(
    val x: Float,
    val y: Float,
    val radius: Float,
    val twinkleSec: Float,
    val phase: Float,
)

/**
 * The effect of the final slide (task 2.5.12, assistant's decision): soft glints that rise slowly and twinkle, over a
 * still fine grain. Radial gradients and points on a canvas, so it looks the same on every Android version without
 * AGSL. Positions are fixed by a seed, the motion is a pure function of [timeSec]; it is inside the slide, so the
 * shared picture has it too.
 */
@Composable
internal fun SummaryGlints(
    timeSec: () -> Float,
    modifier: Modifier = Modifier,
) {
    val glints = remember { glints() }
    val grain = remember { grain() }
    Canvas(modifier) {
        val time = timeSec()
        drawPoints(
            points = grain.map { Offset(it.x * size.width, it.y * size.height) },
            pointMode = PointMode.Points,
            color = MuzzzloColors.TextPrimary.copy(alpha = GRAIN_ALPHA),
            strokeWidth = GRAIN_STROKE,
        )
        val longer = max(size.width, size.height)
        for (glint in glints) {
            val y = ((glint.y - time / RISE_PERIOD_SEC) % 1f + 1f) % 1f
            val alpha = GLINT_MAX_ALPHA * (HALF + HALF * sin(TWO_PI * time / glint.twinkleSec + glint.phase))
            val centre = Offset(glint.x * size.width, y * size.height)
            val radius = glint.radius * longer
            drawCircle(
                brush =
                    Brush.radialGradient(
                        listOf(MuzzzloColors.TextPrimary.copy(alpha = alpha), Color.Transparent),
                        centre,
                        radius,
                    ),
                radius = radius,
                center = centre,
            )
        }
    }
}

private fun glints(): List<Glint> {
    val random = Random(SEED)
    return List(GLINTS) {
        Glint(
            x = random.nextFloat(),
            y = random.nextFloat(),
            radius = GLINT_MIN_RADIUS + random.nextFloat() * GLINT_RADIUS_SPREAD,
            twinkleSec = TWINKLE_MIN_SEC + random.nextFloat() * TWINKLE_SPREAD_SEC,
            phase = random.nextFloat() * TWO_PI,
        )
    }
}

private fun grain(): List<Offset> {
    val random = Random(SEED + 1)
    return List(GRAIN_POINTS) { Offset(random.nextFloat(), random.nextFloat()) }
}
