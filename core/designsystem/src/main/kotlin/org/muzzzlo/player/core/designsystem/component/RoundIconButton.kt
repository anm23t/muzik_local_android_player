package org.muzzzlo.player.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.designsystem.theme.MuzzzloTheme

private const val DISABLED_ALPHA = 0.38f

/** Button and icon sizes from the mockup. */
enum class RoundIconButtonSize(
    val size: Dp,
    val iconSize: Dp,
) {
    /** Dialog buttons and the "more" button of a track row (41:894). */
    Small(MuzzzloDimens.ButtonSmall, MuzzzloDimens.SmallIcon),

    /** Play / pause in the mini player (15:51). */
    Compact(MuzzzloDimens.ButtonCompact, MuzzzloDimens.IconCompact),

    /** Header buttons: back, home, more (3:6, 3:11). */
    Medium(MuzzzloDimens.ButtonMedium, MuzzzloDimens.IconMedium),

    /** Shuffle, repeat and bottom navigation (3:26, 27:5). */
    Large(MuzzzloDimens.ButtonLarge, MuzzzloDimens.IconMedium),

    /** Previous and next (3:29). */
    Control(MuzzzloDimens.ButtonControl, MuzzzloDimens.IconControl),

    /** The big play / pause button (3:31). */
    Primary(MuzzzloDimens.ButtonPrimary, MuzzzloDimens.IconPrimary),
}

/**
 * Round button with an icon, the main control of the mockup.
 *
 * @param accented accent border and the dark accent background (play / pause, "New sort").
 * @param containerColor background of a plain button; dialogs use [MuzzzloColors.Surface].
 * @param iconTint defaults to white on accented buttons and to the secondary text color otherwise.
 */
@Composable
fun RoundIconButton(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: RoundIconButtonSize = RoundIconButtonSize.Medium,
    accented: Boolean = false,
    enabled: Boolean = true,
    containerColor: Color = MuzzzloColors.SurfaceElevated,
    iconTint: Color = if (accented) MuzzzloColors.TextPrimary else MuzzzloColors.TextSecondary,
) {
    val accent = MuzzzloTheme.accent
    val background = if (accented) accent.container else containerColor
    val border = if (accented) accent.accent else MuzzzloColors.Outline
    val glow =
        if (accented && size == RoundIconButtonSize.Primary) {
            Modifier.shadow(
                elevation = MuzzzloDimens.PrimaryButtonGlow,
                shape = CircleShape,
                ambientColor = accent.shadow,
                spotColor = accent.shadow,
            )
        } else {
            Modifier
        }
    Box(
        modifier
            .then(glow)
            .size(size.size)
            .clip(CircleShape)
            .background(background)
            .border(MuzzzloDimens.BorderWidth, border, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(size.iconSize),
            tint = if (enabled) iconTint else iconTint.copy(alpha = DISABLED_ALPHA),
        )
    }
}
