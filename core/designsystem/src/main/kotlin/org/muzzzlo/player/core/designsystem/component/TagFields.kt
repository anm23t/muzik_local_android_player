package org.muzzzlo.player.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import org.muzzzlo.player.core.designsystem.R
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.designsystem.theme.MuzzzloTheme

/** Lines a multi-line field shows before it scrolls inside the screen. */
private const val MULTI_LINE_MIN_LINES = 4

/**
 * Text field of the tag editor: a caption above a dialog row (15:124) that holds the text. The border turns to
 * the accent while the field has focus or shows [error]. Not in the mockup, see DESIGN.md, section 7.
 *
 * @param label caption above the field, in the style of the artist line of a track row (4:337).
 * @param singleLine false for long text (lyrics): the field grows with its content.
 * @param numeric digits keyboard, for numbers and the year.
 * @param error message under the field; null when the value is fine.
 */
@Composable
fun TagTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    numeric: Boolean = false,
    error: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.SmallGap)) {
        FieldLabel(label)
        FieldBox(
            value = value,
            onValueChange = onValueChange,
            description = label,
            singleLine = singleLine,
            numeric = numeric,
            highlighted = error != null,
        )
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MuzzzloColors.TextPrimary)
        }
    }
}

/**
 * Field with several values, one row each: artists, album artists, genres. Each row has a remove button
 * (32, 41:894) and an action row (15:124) under the list adds an empty value. Not in the mockup, see DESIGN.md,
 * section 7.
 *
 * Empty rows stay while the user types; dropping blank values is up to the caller.
 */
@Composable
fun ValueListField(
    label: String,
    values: List<String>,
    onValuesChange: (List<String>) -> Unit,
    addLabel: String,
    modifier: Modifier = Modifier,
    @DrawableRes addIcon: Int = R.drawable.ic_plus,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.SmallGap)) {
        FieldLabel(label)
        values.forEachIndexed { index, value ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.ItemGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FieldBox(
                    value = value,
                    onValueChange = { changed -> onValuesChange(values.replaceAt(index, changed)) },
                    description = label,
                    singleLine = true,
                    numeric = false,
                    highlighted = false,
                    modifier = Modifier.weight(1f),
                )
                RoundIconButton(
                    icon = R.drawable.ic_close,
                    contentDescription = stringResource(R.string.designsystem_remove_value, value),
                    onClick = { onValuesChange(values.filterIndexed { i, _ -> i != index }) },
                    size = RoundIconButtonSize.Small,
                )
            }
        }
        ActionRow(icon = addIcon, text = addLabel, onClick = { onValuesChange(values + "") })
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MuzzzloColors.TextSecondary)
}

@Composable
private fun FieldBox(
    value: String,
    onValueChange: (String) -> Unit,
    description: String,
    singleLine: Boolean,
    numeric: Boolean,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = MuzzzloTheme.accent.accent
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val border = if (focused || highlighted) accent else MuzzzloColors.Outline
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = description },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MuzzzloColors.TextPrimary),
        cursorBrush = SolidColor(accent),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else MULTI_LINE_MIN_LINES,
        keyboardOptions =
            if (numeric) {
                KeyboardOptions(keyboardType = KeyboardType.Number)
            } else {
                KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            },
        interactionSource = interaction,
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MuzzzloDimens.DialogRowHeight)
                    .background(MuzzzloColors.SurfaceElevated, MuzzzloDimens.RowShape)
                    .border(MuzzzloDimens.BorderWidth, border, MuzzzloDimens.RowShape)
                    .padding(
                        horizontal = MuzzzloDimens.DialogRowHorizontalPadding,
                        vertical = MuzzzloDimens.RowPadding,
                    ),
                contentAlignment = Alignment.CenterStart,
            ) { inner() }
        },
    )
}

private fun List<String>.replaceAt(
    index: Int,
    value: String,
): List<String> = mapIndexed { i, old -> if (i == index) value else old }
