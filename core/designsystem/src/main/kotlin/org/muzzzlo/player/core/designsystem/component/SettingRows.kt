package org.muzzzlo.player.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import org.muzzzlo.player.core.designsystem.R
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.designsystem.theme.MuzzzloTheme

/**
 * Settings row with a switch on the right, built from the unselected sort option (15:124); the switch is
 * the accent when on. Not in the mockup, see DESIGN.md, section 7.
 *
 * @param secondary optional explanation under [text].
 */
@Composable
fun SwitchRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    secondary: String? = null,
) {
    val accent = MuzzzloTheme.accent.accent
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = MuzzzloDimens.DialogRowHeight)
            .clip(MuzzzloDimens.RowShape)
            .background(MuzzzloColors.SurfaceElevated)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = MuzzzloDimens.DialogRowHorizontalPadding, vertical = MuzzzloDimens.ItemGap),
        horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.ContentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(text, secondary, Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = MuzzzloColors.Background,
                    checkedTrackColor = accent,
                    checkedBorderColor = accent,
                    uncheckedThumbColor = MuzzzloColors.TextSecondary,
                    uncheckedTrackColor = MuzzzloColors.Surface,
                    uncheckedBorderColor = MuzzzloColors.TextSecondary,
                ),
        )
    }
}

/**
 * Settings row that shows the current [value] on the right and opens a choice or a screen when pressed;
 * without [value] it shows a chevron, unless [chevron] is false (a row that acts at once).
 * Same look as [SwitchRow]. Not in the mockup, see DESIGN.md, section 7.
 */
@Composable
fun ValueRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    secondary: String? = null,
    chevron: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = MuzzzloDimens.DialogRowHeight)
            .clip(MuzzzloDimens.RowShape)
            .background(MuzzzloColors.SurfaceElevated)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = MuzzzloDimens.DialogRowHorizontalPadding, vertical = MuzzzloDimens.ItemGap),
        horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.ContentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(text, secondary, Modifier.weight(1f))
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MuzzzloTheme.accent.accent,
                maxLines = 1,
            )
        } else if (chevron) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right_small),
                contentDescription = null,
                modifier = Modifier.size(MuzzzloDimens.SmallIcon),
                tint = MuzzzloColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun RowText(
    text: String,
    secondary: String?,
    modifier: Modifier,
) {
    Column(modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MuzzzloColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (secondary != null) {
            Text(
                text = secondary,
                style = MaterialTheme.typography.bodySmall,
                color = MuzzzloColors.TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
