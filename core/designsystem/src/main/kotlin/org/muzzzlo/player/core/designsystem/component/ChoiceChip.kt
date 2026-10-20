package org.muzzzlo.player.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.designsystem.theme.MuzzzloTheme

/**
 * One option of a short row of mutually exclusive choices, such as the periods of the statistics. The chosen chip is
 * drawn like [PillButton] (accent container and border), the others have only the quiet border of a panel.
 */
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MuzzzloTheme.accent
    Text(
        text = text,
        modifier =
            modifier
                .height(MuzzzloDimens.PillHeight)
                .clip(MuzzzloDimens.PillShape)
                .background(if (selected) accent.container else MuzzzloColors.Background)
                .border(
                    MuzzzloDimens.BorderWidth,
                    if (selected) accent.accent else MuzzzloColors.Outline,
                    MuzzzloDimens.PillShape,
                )
                .semantics { this.selected = selected }
                .clickable(role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = MuzzzloDimens.DialogRowHorizontalPadding)
                .wrapContentHeight(Alignment.CenterVertically),
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MuzzzloColors.TextPrimary else MuzzzloColors.TextSecondary,
        maxLines = 1,
    )
}
