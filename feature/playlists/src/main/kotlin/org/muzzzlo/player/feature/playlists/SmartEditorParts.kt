package org.muzzzlo.player.feature.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import org.muzzzlo.player.core.designsystem.component.ActionRow
import org.muzzzlo.player.core.designsystem.component.ChoiceChip
import org.muzzzlo.player.core.designsystem.component.MuzzzloDialog
import org.muzzzlo.player.core.designsystem.component.OutlinedPanel
import org.muzzzlo.player.core.designsystem.component.RadioOptionRow
import org.muzzzlo.player.core.designsystem.component.RoundIconButton
import org.muzzzlo.player.core.designsystem.component.RoundIconButtonSize
import org.muzzzlo.player.core.designsystem.component.SearchField
import org.muzzzlo.player.core.designsystem.component.TagTextField
import org.muzzzlo.player.core.designsystem.component.ValueRow
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.model.NameNormalizer
import org.muzzzlo.player.core.model.RuleField
import org.muzzzlo.player.core.model.RuleMatch
import org.muzzzlo.player.core.model.RuleOperator
import org.muzzzlo.player.core.model.RuleValueKind
import org.muzzzlo.player.core.designsystem.R as DesignR

/** Names shown at once in the value dialog; typing narrows the list (assistant's decision). */
private const val MAX_NAMES_SHOWN = 50

/** "All / Any" as two chips after a label, as the period row of the statistics screen. */
@Composable
internal fun MatchChips(
    label: String,
    match: RuleMatch,
    onMatch: (RuleMatch) -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.ItemGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = MuzzzloColors.TextPrimary,
        )
        RuleMatch.entries.forEach { ChoiceChip(matchLabel(it), selected = it == match, onClick = { onMatch(it) }) }
        trailing()
    }
}

/** One group: its "All / Any", its conditions, "Add condition" and the button that removes it. */
@Composable
internal fun GroupPanel(
    number: Int,
    group: GroupDraft,
    choices: RuleChoices,
    onEdit: (DraftEdit) -> Unit,
) {
    OutlinedPanel {
        MatchChips(
            label = stringResource(R.string.smart_editor_group, number),
            match = group.match,
            onMatch = { onEdit(DraftEdit.GroupMatch(group.id, it)) },
            trailing = {
                RoundIconButton(
                    icon = DesignR.drawable.ic_close,
                    contentDescription = stringResource(R.string.smart_editor_group_remove),
                    onClick = { onEdit(DraftEdit.RemoveGroup(group.id)) },
                    size = RoundIconButtonSize.Small,
                    containerColor = MuzzzloColors.Surface,
                )
            },
        )
        group.conditions.forEach { condition -> ConditionEditor(group.id, condition, choices, onEdit) }
        ActionRow(
            icon = DesignR.drawable.ic_plus,
            text = stringResource(R.string.smart_editor_condition_add),
            onClick = { onEdit(DraftEdit.AddCondition(group.id)) },
        )
    }
}

/** Which list dialog of a condition is open. */
private enum class ConditionDialog { None, Field, Operator, Name }

/** A condition: field and operator as rows with a choice dialog, then the value its kind needs. */
@Composable
private fun ConditionEditor(
    groupId: Long,
    condition: ConditionDraft,
    choices: RuleChoices,
    onEdit: (DraftEdit) -> Unit,
) {
    var dialog by rememberSaveable(condition.id) { mutableStateOf(ConditionDialog.None) }
    val close = { dialog = ConditionDialog.None }
    // Without this the name field takes the focus back when a dialog closes and the keyboard covers the editor.
    val focus = LocalFocusManager.current
    val open = { which: ConditionDialog ->
        focus.clearFocus()
        dialog = which
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        ValueRow(
            text = stringResource(R.string.smart_editor_field),
            onClick = { open(ConditionDialog.Field) },
            modifier = Modifier.weight(1f),
            value = fieldLabel(condition.field),
        )
        RoundIconButton(
            icon = DesignR.drawable.ic_close,
            contentDescription = stringResource(R.string.smart_editor_condition_remove),
            onClick = { onEdit(DraftEdit.RemoveCondition(groupId, condition.id)) },
            size = RoundIconButtonSize.Small,
            containerColor = MuzzzloColors.Surface,
        )
    }
    if (condition.field.operators.size > 1) {
        ValueRow(
            text = stringResource(R.string.smart_editor_operator),
            onClick = { open(ConditionDialog.Operator) },
            value = operatorLabel(condition.operator, condition.field),
        )
    }
    ConditionValue(groupId, condition, onChoose = { open(ConditionDialog.Name) }, onEdit = onEdit)
    when (dialog) {
        ConditionDialog.None -> Unit
        ConditionDialog.Field ->
            ListDialog(stringResource(R.string.smart_editor_field), RuleField.entries, condition.field, close, {
                fieldLabel(it)
            }) { onEdit(DraftEdit.Field(groupId, condition.id, it)) }
        ConditionDialog.Operator ->
            ListDialog(
                stringResource(R.string.smart_editor_operator),
                condition.field.operators,
                condition.operator,
                close,
                { operatorLabel(it, condition.field) },
            ) { onEdit(DraftEdit.Operator(groupId, condition.id, it)) }
        ConditionDialog.Name ->
            NameDialog(choices.namesFor(condition.field), condition.value, close) {
                onEdit(DraftEdit.Value(groupId, condition.id, it))
            }
    }
}

/** The value of a condition: a name from the library, a number, two numbers for "between", or nothing. */
@Composable
private fun ConditionValue(
    groupId: Long,
    condition: ConditionDraft,
    onChoose: () -> Unit,
    onEdit: (DraftEdit) -> Unit,
) {
    if (!condition.needsValue) return
    if (condition.field.kind == RuleValueKind.TEXT) {
        ValueRow(
            text = stringResource(R.string.smart_editor_value),
            onClick = onChoose,
            value = condition.value.ifBlank { stringResource(R.string.smart_editor_value_choose) },
        )
        return
    }
    val hint = if (condition.field == RuleField.RATING) stringResource(R.string.smart_editor_rating_hint) else null
    if (condition.operator == RuleOperator.BETWEEN) {
        Row(horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.ItemGap)) {
            TagTextField(
                label = stringResource(R.string.smart_editor_value_from),
                value = condition.value,
                onValueChange = { onEdit(DraftEdit.Value(groupId, condition.id, it)) },
                modifier = Modifier.weight(1f),
                numeric = true,
            )
            TagTextField(
                label = stringResource(R.string.smart_editor_value_to),
                value = condition.upTo,
                onValueChange = { onEdit(DraftEdit.UpTo(groupId, condition.id, it)) },
                modifier = Modifier.weight(1f),
                numeric = true,
            )
        }
    } else {
        TagTextField(
            label = valueLabel(condition.field),
            value = condition.value,
            onValueChange = { onEdit(DraftEdit.Value(groupId, condition.id, it)) },
            numeric = true,
            hint = hint,
        )
    }
}

/** A single choice from a short list, as the sort dialog (15:111). */
@Composable
internal fun <T> ListDialog(
    title: String,
    options: List<T>,
    selected: T,
    onDismiss: () -> Unit,
    label: @Composable (T) -> String,
    onChoose: (T) -> Unit,
) {
    MuzzzloDialog(title = title, onDismiss = onDismiss) {
        options.forEach { option ->
            RadioOptionRow(
                text = label(option),
                selected = option == selected,
                onClick = {
                    onChoose(option)
                    onDismiss()
                },
            )
        }
    }
}

/** A name from the library, narrowed by the search field; the typed text itself can be used too ("contains"). */
@Composable
private fun NameDialog(
    names: List<String>,
    current: String,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf(current) }
    val choose = { name: String ->
        onChoose(name)
        onDismiss()
    }
    MuzzzloDialog(title = stringResource(R.string.smart_editor_value), onDismiss = onDismiss) {
        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.smart_editor_value_search),
            autoFocus = true,
        )
        val typed = query.trim()
        if (typed.isNotEmpty()) {
            ActionRow(
                icon = DesignR.drawable.ic_pencil,
                text = stringResource(R.string.smart_editor_value_use, typed),
                onClick = { choose(typed) },
            )
        }
        val key = NameNormalizer.key(typed)
        names.asSequence().filter { key.isEmpty() || key in NameNormalizer.key(it) }.take(MAX_NAMES_SHOWN).forEach {
            RadioOptionRow(text = it, selected = it == current, onClick = { choose(it) })
        }
    }
}
