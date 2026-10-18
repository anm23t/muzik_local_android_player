package org.muzzzlo.player.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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

/** Accent of the current composition. Provided by [MuzzzloTheme], dynamic from task 0.3.8. */
val LocalAccent = staticCompositionLocalOf { AccentColors.from(DefaultAccent) }

/**
 * App theme. Dark only, there is no light variant on purpose (CLAUDE.md, decision 8).
 *
 * @param accent accent color; everything "red" in the interface follows it.
 */
@Composable
fun MuzzzloTheme(
    accent: Color = DefaultAccent,
    content: @Composable () -> Unit,
) {
    val accentColors = remember(accent) { AccentColors.from(accent) }
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

/** Material 3 scheme for components that are still Material (for example the queue until 1.0.1). */
private fun colorSchemeFor(accent: AccentColors) =
    darkColorScheme(
        primary = accent.accent,
        onPrimary = MuzzzloColors.TextPrimary,
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
