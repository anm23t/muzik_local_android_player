package org.muzzzlo.player.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.MuzzzloDialog
import org.muzzzlo.player.core.designsystem.component.OutlinedPanel
import org.muzzzlo.player.core.designsystem.component.PillButton
import org.muzzzlo.player.core.designsystem.component.RoundIconButton
import org.muzzzlo.player.core.designsystem.component.ScreenFrame
import org.muzzzlo.player.core.designsystem.component.SectionTitle
import org.muzzzlo.player.core.designsystem.component.TagTextField
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.model.CompoundRule
import org.muzzzlo.player.core.designsystem.R as DesignR

/**
 * Compound names (no mockup, DESIGN.md, section 7): a field to add a name on top, then every rule as a row with
 * its origin and a button on the right (remove, or switch a built-in rule off or on).
 */
@Composable
fun CompoundNamesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CompoundNamesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScreenFrame(modifier) {
        ScreenHeader(onBack = onBack)
        SectionTitle(stringResource(R.string.compound_title))
        if (!state.isLoaded) {
            Notice(stringResource(R.string.check_loading))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.ContentGap)) {
                item {
                    AddName(state, viewModel::onInputChanged, viewModel::onAddClicked)
                }
                if (state.rules.isEmpty()) {
                    item { Text(stringResource(R.string.compound_empty), color = MuzzzloColors.TextSecondary) }
                }
                items(state.rules, key = { it.id }) { rule ->
                    RuleRow(
                        rule,
                        onRemove = { viewModel.onRemoveClicked(rule) },
                        onEnable = { viewModel.onEnableClicked(rule) },
                    )
                }
            }
        }
    }
    state.confirm?.let { confirm ->
        MuzzzloDialog(
            title = stringResource(R.string.compound_remove_title, confirm.rule.displayName),
            onDismiss = viewModel::onRemoveDismissed,
        ) {
            Text(
                text =
                    pluralStringResource(
                        R.plurals.compound_remove_warning,
                        confirm.affectedTracks,
                        confirm.affectedTracks,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MuzzzloColors.TextPrimary,
            )
            PillButton(
                text =
                    stringResource(
                        if (confirm.rule.builtinId == null) R.string.compound_delete else R.string.compound_switch_off,
                    ),
                onClick = viewModel::onRemoveConfirmed,
                wide = true,
            )
        }
    }
}

@Composable
private fun AddName(
    state: CompoundNamesUiState,
    onChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.ContentGap)) {
        TagTextField(
            label = stringResource(R.string.compound_add_label),
            value = state.input,
            onValueChange = onChange,
            hint = stringResource(R.string.compound_add_hint),
            error =
                when (state.inputError) {
                    CompoundInputError.TooShort -> stringResource(R.string.remember_too_short)
                    CompoundInputError.Duplicate -> stringResource(R.string.remember_duplicate)
                    null -> null
                },
        )
        PillButton(
            text = stringResource(R.string.compound_add),
            onClick = onAdd,
            wide = true,
            enabled = state.input.isNotBlank() && !state.isBusy,
        )
    }
}

@Composable
private fun RuleRow(
    rule: CompoundRule,
    onRemove: () -> Unit,
    onEnable: () -> Unit,
) {
    val off = !rule.enabled
    OutlinedPanel {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.ItemGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = rule.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (off) MuzzzloColors.TextSecondary else MuzzzloColors.TextPrimary,
                )
                Text(
                    text = stringResource(ruleOrigin(rule)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MuzzzloColors.TextSecondary,
                )
            }
            if (off) {
                RoundIconButton(
                    icon = DesignR.drawable.ic_plus,
                    contentDescription = stringResource(R.string.compound_switch_on),
                    onClick = onEnable,
                )
            } else {
                RoundIconButton(
                    icon = DesignR.drawable.ic_close,
                    contentDescription =
                        stringResource(
                            if (rule.builtinId == null) R.string.compound_delete else R.string.compound_switch_off,
                        ),
                    onClick = onRemove,
                )
            }
        }
    }
}

private fun ruleOrigin(rule: CompoundRule): Int =
    when {
        rule.builtinId == null -> R.string.compound_origin_own
        rule.enabled -> R.string.compound_origin_builtin
        else -> R.string.compound_origin_off
    }
