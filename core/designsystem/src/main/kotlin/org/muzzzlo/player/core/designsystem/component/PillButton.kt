package org.muzzzlo.player.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.designsystem.theme.MuzzzloTheme

/**
 * Accent "pill" button: "Play" and "Sort" (4:333, 15:14), or "Done" across the dialog (15:142).
 *
 * @param wide full width, 40 high, used at the bottom of dialogs.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    wide: Boolean = false,
) {
    val accent = MuzzzloTheme.accent
    val sizeModifier =
        if (wide) {
            Modifier.fillMaxWidth().height(MuzzzloDimens.WidePillHeight)
        } else {
            Modifier.height(MuzzzloDimens.PillHeight)
        }
    Row(
        modifier
            .then(sizeModifier)
            .clip(MuzzzloDimens.PillShape)
            .background(accent.container)
            .border(MuzzzloDimens.BorderWidth, accent.accent, MuzzzloDimens.PillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = MuzzzloDimens.DialogRowHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.SmallGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, Modifier.size(MuzzzloDimens.SmallIcon), tint = accent.accent)
        }
        Text(
            text = text,
            style =
                if (wide) {
                    MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                } else {
                    MaterialTheme.typography.labelLarge
                },
            color = MuzzzloColors.TextPrimary,
            maxLines = 1,
        )
    }
}
