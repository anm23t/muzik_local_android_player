package org.muzzzlo.player.core.designsystem.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Default accent: the red of the mockup (DESIGN.md, section 2). */
val DefaultAccent = Color(0xFFCD253E)

/**
 * Fixed colors of the dark-only theme (DESIGN.md, section 2). The accent is not here:
 * it changes with the cover and is read from [MuzzzloTheme.accent].
 */
object MuzzzloColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF0B0B0B)
    val SurfaceElevated = Color(0xFF121212)
    val Outline = Color(0xFF1F1F1F)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF9CA3AF)

    /** Border of the empty artist photo (mockup node 23:478). */
    val PhotoPlaceholderBorder = TextPrimary.copy(alpha = 0.2f)

    /** Shadow of dialogs (mockup node 15:111). */
    val DialogShadow = Color.Black.copy(alpha = 0.6f)
}

/**
 * Accent of the current composition. Provided by [MuzzzloTheme]. It changes on every frame of the color
 * animation, so it is not a static local: only the components that read it are redrawn.
 */
val LocalAccent = compositionLocalOf { AccentColors.from(DefaultAccent) }

/** Above this luminance white on the accent is below 3:1, so content on it turns black. */
private const val LIGHT_ACCENT_LUMINANCE = 0.3f

/** Duration of the accent change when the playing track changes (DESIGN.md, section 4: 300 to 500 ms). */
const val ACCENT_ANIMATION_MS = 400

/**
 * App theme. Dark only, there is no light variant on purpose (CLAUDE.md, decision 8).
 *
 * @param accent accent color; everything "red" in the interface follows it. A new value fades in over
 *   [ACCENT_ANIMATION_MS] (DESIGN.md, section 4).
 */
@Composable
fun MuzzzloTheme(
    accent: Color = DefaultAccent,
    content: @Composable () -> Unit,
) {
    val animated by animateColorAsState(accent, tween(ACCENT_ANIMATION_MS), label = "accent")
    val accentColors = remember(animated) { AccentColors.from(animated) }
    CompositionLocalProvider(LocalAccent provides accentColors) {
        MaterialTheme(
            colorScheme = colorSchemeFor(accentColors),
            typography = MuzzzloTypography,
            content = content,
        )
    }
}

/** Shortcuts to the theme values, in the style of [MaterialTheme]. */
object MuzzzloTheme {
    val accent: AccentColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAccent.current
}

/** Content color on an accent fill: black on a light neutral accent, white otherwise. */
private fun onAccentOf(accent: Color): Color =
    if (accent.luminance() > LIGHT_ACCENT_LUMINANCE) MuzzzloColors.Background else MuzzzloColors.TextPrimary

/** Material 3 scheme for components that are still Material (for example the queue until 1.0.1). */
private fun colorSchemeFor(accent: AccentColors) =
    darkColorScheme(
        primary = accent.accent,
        // A neutral accent of a colorless cover can be almost white (DESIGN.md, section 4).
        onPrimary = onAccentOf(accent.accent),
        primaryContainer = accent.container,
        onPrimaryContainer = MuzzzloColors.TextPrimary,
        // Neutral instead of the Material defaults, for example the inactive slider track.
        secondary = MuzzzloColors.TextSecondary,
        onSecondary = MuzzzloColors.Background,
        secondaryContainer = MuzzzloColors.Outline,
        onSecondaryContainer = MuzzzloColors.TextPrimary,
        tertiary = MuzzzloColors.TextSecondary,
        onTertiary = MuzzzloColors.Background,
        tertiaryContainer = MuzzzloColors.Outline,
        onTertiaryContainer = MuzzzloColors.TextPrimary,
        background = MuzzzloColors.Background,
        onBackground = MuzzzloColors.TextPrimary,
        surface = MuzzzloColors.Background,
        onSurface = MuzzzloColors.TextPrimary,
        surfaceVariant = MuzzzloColors.SurfaceElevated,
        onSurfaceVariant = MuzzzloColors.TextSecondary,
        surfaceContainerLowest = MuzzzloColors.Background,
        surfaceContainerLow = MuzzzloColors.Surface,
        surfaceContainer = MuzzzloColors.SurfaceElevated,
        surfaceContainerHigh = MuzzzloColors.SurfaceElevated,
        surfaceContainerHighest = MuzzzloColors.SurfaceElevated,
        outline = MuzzzloColors.Outline,
        outlineVariant = MuzzzloColors.Outline,
    )
