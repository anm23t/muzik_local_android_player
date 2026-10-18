package org.muzzzlo.player.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/** Lightness of [AccentColors.container]: the default red gives the mockup's #241015. */
private const val CONTAINER_LIGHTNESS = 0.102f

/** Share of the accent saturation kept in [AccentColors.container]. */
private const val CONTAINER_SATURATION_FACTOR = 0.554f

/** Opacity of [AccentColors.shadow] (DESIGN.md, section 2). */
private const val SHADOW_ALPHA = 0.2f

private const val HUE_SECTOR_DEGREES = 60f
private const val FULL_TURN_DEGREES = 360f
private const val GREEN_SECTOR_OFFSET = 2f
private const val BLUE_SECTOR_OFFSET = 4f
private const val HUE_SECTORS = 6f

/**
 * The accent and the tones derived from it (DESIGN.md, sections 2 and 4).
 *
 * @property accent buttons, progress fill, the current track.
 * @property container dark tone of the accent: background of "Play", "Sort", "Done" and selected rows.
 * @property shadow shadow of the big play button.
 */
@Immutable
data class AccentColors(
    val accent: Color,
    val container: Color,
    val shadow: Color,
) {
    companion object {
        /** Derives the container and the shadow from [accent]; nothing is stored separately. */
        fun from(accent: Color): AccentColors =
            AccentColors(
                accent = accent,
                container = containerToneOf(accent),
                shadow = accent.copy(alpha = SHADOW_ALPHA),
            )
    }
}

/** Same hue, a fixed dark lightness and a reduced saturation. */
internal fun containerToneOf(accent: Color): Color {
    val hsl = accent.toHsl()
    return Color.hsl(
        hue = hsl.hue,
        saturation = (hsl.saturation * CONTAINER_SATURATION_FACTOR).coerceIn(0f, 1f),
        lightness = CONTAINER_LIGHTNESS,
    )
}

/** Hue in degrees [0, 360), saturation and lightness in [0, 1]. */
internal data class Hsl(
    val hue: Float,
    val saturation: Float,
    val lightness: Float,
)

internal fun Color.toHsl(): Hsl {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val lightness = (max + min) / 2
    val delta = max - min
    if (delta == 0f) return Hsl(hue = 0f, saturation = 0f, lightness = lightness)
    val saturation = delta / (1 - abs(2 * lightness - 1))
    val sector =
        when (max) {
            red -> ((green - blue) / delta).mod(HUE_SECTORS)
            green -> (blue - red) / delta + GREEN_SECTOR_OFFSET
            else -> (red - green) / delta + BLUE_SECTOR_OFFSET
        }
    val hue = (sector * HUE_SECTOR_DEGREES).mod(FULL_TURN_DEGREES)
    return Hsl(hue = hue, saturation = saturation.coerceIn(0f, 1f), lightness = lightness)
}
