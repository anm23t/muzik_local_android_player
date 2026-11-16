package org.muzzzlo.player.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/** Direction of a gradient cover; the mockup (28:620) has both. */
enum class GradientDirection {
    /** From the top left corner to the bottom right one. */
    DIAGONAL,

    /** From the left edge to the right one. */
    HORIZONTAL,
}

/** Two colors and a direction: one gradient cover. */
data class GradientPair(
    val start: Color,
    val end: Color,
    val direction: GradientDirection,
)

/**
 * Covers of generated and smart playlists (task 2.5.7, DESIGN.md): linear gradients of two colors, without text.
 * The index comes from `CoverGradients` of `core:model`, which counts exactly [pairs]`.size` (12) pairs. The dynamic
 * accent does not touch them.
 */
object MuzzzloCoverGradients {
    val pairs: List<GradientPair> =
        listOf(
            // The ready-made sets, in the order of GeneratedKind: On Repeat, Forgotten Favorites, Best Rated,
            // Recently Added.
            GradientPair(Color(0xFFFF5F6D), Color(0xFFFFC371), GradientDirection.DIAGONAL),
            GradientPair(Color(0xFF7F7FD5), Color(0xFF86A8E7), GradientDirection.HORIZONTAL),
            GradientPair(Color(0xFFF7971E), Color(0xFFFFD200), GradientDirection.DIAGONAL),
            GradientPair(Color(0xFF11998E), Color(0xFF38EF7D), GradientDirection.HORIZONTAL),
            // Seasons and the year take the next ones; the daily mix goes through all twelve by date.
            GradientPair(Color(0xFFFC466B), Color(0xFF3F5EFB), GradientDirection.DIAGONAL),
            GradientPair(Color(0xFF00C6FF), Color(0xFF0072FF), GradientDirection.HORIZONTAL),
            GradientPair(Color(0xFF8E2DE2), Color(0xFF4A00E0), GradientDirection.DIAGONAL),
            GradientPair(Color(0xFFF953C6), Color(0xFFB91D73), GradientDirection.HORIZONTAL),
            GradientPair(Color(0xFF43CEA2), Color(0xFF185A9D), GradientDirection.DIAGONAL),
            GradientPair(Color(0xFFFF512F), Color(0xFFDD2476), GradientDirection.HORIZONTAL),
            GradientPair(Color(0xFFDA22FF), Color(0xFF9733EE), GradientDirection.DIAGONAL),
            GradientPair(Color(0xFFF2994A), Color(0xFFF2C94C), GradientDirection.HORIZONTAL),
        )

    /** The pair at [index]; an index out of range wraps, so a stored value never breaks the screen. */
    fun pair(index: Int): GradientPair = pairs[Math.floorMod(index, pairs.size)]
}
