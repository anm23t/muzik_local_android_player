package org.muzzzlo.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFFCD253E)
private val AccentContainer = Color(0xFF241015)

/**
 * Dark-only theme with the default red accent (DESIGN.md, section 2).
 * Replaced by `core:designsystem` with the dynamic cover accent in task 0.3.3 / 0.3.7.
 */
@Composable
fun MuzzzloTheme(content: @Composable () -> Unit) {
    val colors =
        darkColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            primaryContainer = AccentContainer,
            onPrimaryContainer = Color.White,
            background = Color.Black,
            onBackground = Color.White,
            surface = Color(0xFF121212),
            onSurface = Color.White,
            surfaceVariant = Color(0xFF121212),
            onSurfaceVariant = Color(0xFF9CA3AF),
            outline = Color(0xFF1F1F1F),
        )
    MaterialTheme(colorScheme = colors, content = content)
}
