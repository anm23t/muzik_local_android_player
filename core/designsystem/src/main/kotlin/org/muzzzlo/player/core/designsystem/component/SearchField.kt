package org.muzzzlo.player.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import org.muzzzlo.player.core.designsystem.R
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.designsystem.theme.MuzzzloTheme

/**
 * Search field: the magnifier on the left, the query, a clear button on the right while there is text. The box is
 * the tag editor field (rounded row, accent border in focus); not in the mockup, see DESIGN.md, section 7.
 *
 * @param placeholder shown while the field is empty.
 * @param autoFocus takes the focus and opens the keyboard when the field first appears.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
) {
    val accent = MuzzzloTheme.accent.accent
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = placeholder },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MuzzzloColors.TextPrimary),
        cursorBrush = SolidColor(accent),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        interactionSource = interaction,
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MuzzzloDimens.DialogRowHeight)
                    .background(MuzzzloColors.SurfaceElevated, MuzzzloDimens.RowShape)
                    .border(
                        MuzzzloDimens.BorderWidth,
                        if (focused) accent else MuzzzloColors.Outline,
                        MuzzzloDimens.RowShape,
                    )
                    .padding(start = MuzzzloDimens.DialogRowHorizontalPadding, end = MuzzzloDimens.SmallGap),
                horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.ItemGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    modifier = Modifier.size(MuzzzloDimens.IconCompact),
                    tint = MuzzzloColors.TextSecondary,
                )
                Box(Modifier.weight(1f).padding(vertical = MuzzzloDimens.RowPadding)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MuzzzloColors.TextSecondary,
                            maxLines = 1,
                        )
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    RoundIconButton(
                        icon = R.drawable.ic_close,
                        contentDescription = stringResource(R.string.designsystem_clear_search),
                        onClick = { onValueChange("") },
                        size = RoundIconButtonSize.Small,
                    )
                }
            }
        },
    )
}
