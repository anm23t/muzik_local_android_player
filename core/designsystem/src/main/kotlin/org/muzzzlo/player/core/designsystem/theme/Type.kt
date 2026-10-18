package org.muzzzlo.player.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.muzzzlo.player.core.designsystem.R

/** Variable fonts from google/fonts: one file per family, the weight is set through the `wght` axis. */
@OptIn(ExperimentalTextApi::class)
private fun variableFont(
    resId: Int,
    weight: FontWeight,
) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Headings (DESIGN.md, section 2, "Типографика"). */
val PlayfairDisplay =
    FontFamily(
        variableFont(R.font.playfair_display, FontWeight.Normal),
        variableFont(R.font.playfair_display, FontWeight.SemiBold),
        variableFont(R.font.playfair_display, FontWeight.Bold),
    )

/** Body text, replaces Manuale of the mockup. */
val Literata =
    FontFamily(
        variableFont(R.font.literata, FontWeight.Light),
        variableFont(R.font.literata, FontWeight.Normal),
        variableFont(R.font.literata, FontWeight.Medium),
        variableFont(R.font.literata, FontWeight.SemiBold),
        variableFont(R.font.literata, FontWeight.Bold),
    )

private fun style(
    family: FontFamily,
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
) = TextStyle(fontFamily = family, fontWeight = weight, fontSize = size.sp, lineHeight = lineHeight.sp)

/**
 * The single type scale of the app. The table with the roles is in DESIGN.md, section 2.
 * A highlighted row (current track, selected option) uses the same style with [FontWeight.SemiBold].
 */
val MuzzzloTypography =
    Typography(
        displaySmall = style(PlayfairDisplay, FontWeight.Bold, size = 40, lineHeight = 48),
        headlineLarge = style(PlayfairDisplay, FontWeight.Bold, size = 32, lineHeight = 40),
        headlineMedium = style(PlayfairDisplay, FontWeight.Bold, size = 28, lineHeight = 36),
        headlineSmall = style(PlayfairDisplay, FontWeight.Bold, size = 26, lineHeight = 32),
        titleLarge = style(PlayfairDisplay, FontWeight.Bold, size = 22, lineHeight = 28),
        titleMedium = style(Literata, FontWeight.Bold, size = 20, lineHeight = 26),
        titleSmall = style(Literata, FontWeight.Normal, size = 18, lineHeight = 24),
        bodyLarge = style(Literata, FontWeight.Normal, size = 17, lineHeight = 24),
        bodyMedium = style(Literata, FontWeight.Light, size = 14, lineHeight = 20),
        bodySmall = style(Literata, FontWeight.Light, size = 13, lineHeight = 18),
        labelLarge = style(Literata, FontWeight.SemiBold, size = 14, lineHeight = 20),
        labelMedium = style(Literata, FontWeight.Light, size = 14, lineHeight = 20),
        labelSmall = style(Literata, FontWeight.Light, size = 13, lineHeight = 18),
    )
