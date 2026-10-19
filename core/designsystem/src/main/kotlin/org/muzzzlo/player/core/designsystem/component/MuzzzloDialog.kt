package org.muzzzlo.player.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.muzzzlo.player.core.designsystem.R
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.designsystem.theme.MuzzzloTheme

/**
 * Dialog in the style of the sort dialog (15:111): title, optional header buttons, a close button
 * and the content below. The content scrolls when the screen is short.
 *
 * @param headerActions extra round buttons between the title and the close button ("New sort", 15:114).
 */
@Composable
fun MuzzzloDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    headerActions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier
                .padding(horizontal = MuzzzloDimens.DialogMargin)
                .widthIn(max = MuzzzloDimens.DialogMaxWidth)
                .fillMaxWidth()
                .shadow(
                    elevation = MuzzzloDimens.DialogShadowBlur,
                    shape = MuzzzloDimens.DialogShape,
                    spotColor = MuzzzloColors.DialogShadow,
                ).clip(MuzzzloDimens.DialogShape)
                .background(MuzzzloColors.Background)
                .border(MuzzzloDimens.BorderWidth, MuzzzloColors.Outline, MuzzzloDimens.DialogShape)
                .padding(MuzzzloDimens.DialogPadding),
            verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.SectionGap),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.ContentGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = MuzzzloColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                headerActions()
                RoundIconButton(
                    icon = R.drawable.ic_close,
                    contentDescription = stringResource(R.string.designsystem_close),
                    onClick = onDismiss,
                    size = RoundIconButtonSize.Small,
                    containerColor = MuzzzloColors.Surface,
                )
            }
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.SectionGap),
                content = content,
            )
        }
    }
}

/**
 * Option of a single choice list, as in the sort dialog (15:117 selected, 15:124 not).
 *
 * @param secondary optional second line under [text]; with it the text wraps to [maxLines] lines instead of
 * being cut and the row grows (not in the mockup, DESIGN.md, section 7).
 * @param secondaryMaxLines lines of [secondary] before it is cut with an ellipsis.
 */
@Composable
fun RadioOptionRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    maxLines: Int = 1,
    secondaryMaxLines: Int = 1,
) {
    val accent = MuzzzloTheme.accent
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = MuzzzloDimens.DialogRowHeight)
            .clip(MuzzzloDimens.RowShape)
            .background(if (selected) accent.container else MuzzzloColors.SurfaceElevated)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = MuzzzloDimens.DialogRowHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.ContentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = MuzzzloDimens.TinyGap)) {
            Text(
                text = text,
                style =
                    MaterialTheme.typography.bodyLarge.let {
                        if (selected) it.copy(fontWeight = FontWeight.SemiBold) else it
                    },
                color = MuzzzloColors.TextPrimary,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (secondary != null) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MuzzzloColors.TextSecondary,
                    maxLines = secondaryMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        RadioMark(selected)
    }
}

@Composable
private fun RadioMark(selected: Boolean) {
    val color = if (selected) MuzzzloTheme.accent.accent else MuzzzloColors.TextSecondary
    Canvas(Modifier.size(MuzzzloDimens.RadioSize)) {
        val stroke = MuzzzloDimens.RadioStroke.toPx()
        drawCircle(color = color, radius = (size.minDimension - stroke) / 2, style = Stroke(width = stroke))
        if (selected) drawCircle(color = color, radius = MuzzzloDimens.RadioDot.toPx() / 2)
    }
}

/**
 * Dialog row with an icon on the left: one action of a menu. Built from the unselected sort option
 * (15:124) and the icon with a label of 4:330. Not in the mockup, see DESIGN.md, section 7.
 */
@Composable
fun ActionRow(
    @DrawableRes icon: Int,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = ActionRow(
    text = text,
    onClick = onClick,
    modifier = modifier,
    leading = {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(MuzzzloDimens.SmallIcon),
            tint = MuzzzloColors.TextSecondary,
        )
    },
)

/** The row of [ActionRow] with any [leading] content (for example an artist photo) in place of the icon. */
@Composable
fun ActionRow(
    text: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(MuzzzloDimens.DialogRowHeight)
            .clip(MuzzzloDimens.RowShape)
            .background(MuzzzloColors.SurfaceElevated)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = MuzzzloDimens.DialogRowHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.ContentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MuzzzloColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
