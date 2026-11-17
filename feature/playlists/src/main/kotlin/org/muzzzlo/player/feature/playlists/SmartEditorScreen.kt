package org.muzzzlo.player.feature.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.ActionRow
import org.muzzzlo.player.core.designsystem.component.ChoiceChip
import org.muzzzlo.player.core.designsystem.component.PillButton
import org.muzzzlo.player.core.designsystem.component.ScreenFrame
import org.muzzzlo.player.core.designsystem.component.SectionTitle
import org.muzzzlo.player.core.designsystem.component.TagTextField
import org.muzzzlo.player.core.designsystem.component.ValueRow
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.model.RuleSortField
import org.muzzzlo.player.core.designsystem.R as DesignR

/**
 * The rules editor of a smart playlist (task 2.5.10). Not in the mockup (DESIGN.md, section 7): the frame and fields
 * of the tag editor (15:4, 15:13), the back button (3:6), rows and choices as in the sort dialog (15:111), groups in
 * panels with "All / Any" chips. The number of matching tracks follows every change; "Save" in the header.
 *
 * @param onSaved called with the id of the saved playlist; the app leaves the editor.
 */
@Composable
fun SmartEditorScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SmartPlaylistEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }
    ScreenFrame(modifier) {
        ScreenHeader(onBack) {
            PillButton(text = stringResource(R.string.smart_editor_save), onClick = viewModel::onSave)
        }
        SectionTitle(
            stringResource(if (state.isNew) R.string.smart_editor_title_new else R.string.smart_editor_title_edit),
        )
        if (state.isLoaded) EditorContent(state, viewModel::onEdit)
    }
}

@Composable
private fun EditorContent(
    state: SmartEditorUiState,
    onEdit: (DraftEdit) -> Unit,
) {
    val draft = state.draft
    LazyColumn(verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.SectionGap)) {
        item(key = "name") {
            TagTextField(
                label = stringResource(R.string.smart_editor_name),
                value = draft.name,
                onValueChange = { onEdit(DraftEdit.Name(it)) },
                error =
                    stringResource(
                        R.string.smart_editor_name_missing,
                    ).takeIf { state.showErrors && state.nameMissing },
            )
        }
        item(key = "count") { MatchingCount(state.matchingCount) }
        if (draft.groups.size > 1) {
            item(key = "match") {
                MatchChips(
                    stringResource(R.string.smart_editor_groups_match),
                    draft.match,
                    { onEdit(DraftEdit.Match(it)) },
                )
            }
        }
        itemsIndexed(draft.groups, key = { _, group -> "group-${group.id}" }) { index, group ->
            GroupPanel(index + 1, group, state.choices, onEdit)
        }
        item(key = "add-group") {
            Column(verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.SmallGap)) {
                ActionRow(
                    icon = DesignR.drawable.ic_plus,
                    text = stringResource(R.string.smart_editor_group_add),
                    onClick = { onEdit(DraftEdit.AddGroup) },
                )
                if (state.showErrors && state.conditionsMissing) {
                    ErrorText(stringResource(R.string.smart_editor_conditions_missing))
                }
            }
        }
        item(key = "sort") { SortRows(draft, onEdit) }
        item(key = "limit") {
            TagTextField(
                label = stringResource(R.string.smart_editor_limit),
                value = draft.limit,
                onValueChange = { onEdit(DraftEdit.Limit(it)) },
                numeric = true,
                hint = stringResource(R.string.smart_editor_limit_hint),
            )
        }
    }
}

@Composable
private fun MatchingCount(count: Int?) {
    Text(
        text =
            count?.let { pluralStringResource(R.plurals.smart_editor_matching, it, it) }
                ?: stringResource(R.string.smart_editor_counting),
        style = MaterialTheme.typography.bodyLarge,
        color = MuzzzloColors.TextSecondary,
    )
}

@Composable
private fun ErrorText(text: String) {
    // As the error under a tag field: primary text, the panel above is not highlighted.
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MuzzzloColors.TextPrimary)
}

/** The sort field in a choice dialog and the direction as two chips. */
@Composable
private fun SortRows(
    draft: SmartDraft,
    onEdit: (DraftEdit) -> Unit,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val sort = draft.sort
    Column(verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.SmallGap)) {
        SortChoice(sort.field, sort.descending, onChoose = { choosing = true }, onEdit = onEdit)
    }
    if (choosing) {
        ListDialog(
            title = stringResource(R.string.smart_editor_sort),
            options = RuleSortField.entries,
            selected = sort.field,
            onDismiss = { choosing = false },
            label = { sortLabel(it) },
        ) { onEdit(DraftEdit.Sort(it, sort.descending)) }
    }
}

@Composable
private fun SortChoice(
    field: RuleSortField,
    descending: Boolean,
    onChoose: () -> Unit,
    onEdit: (DraftEdit) -> Unit,
) {
    ValueRow(
        text = stringResource(R.string.smart_editor_sort),
        onClick = onChoose,
        value = sortLabel(field),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.ItemGap)) {
        ChoiceChip(
            text = stringResource(R.string.smart_editor_sort_ascending),
            selected = !descending,
            onClick = { onEdit(DraftEdit.Sort(field, descending = false)) },
        )
        ChoiceChip(
            text = stringResource(R.string.smart_editor_sort_descending),
            selected = descending,
            onClick = { onEdit(DraftEdit.Sort(field, descending = true)) },
        )
    }
}
