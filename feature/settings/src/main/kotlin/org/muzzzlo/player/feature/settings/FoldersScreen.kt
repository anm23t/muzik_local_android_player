package org.muzzzlo.player.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.ScreenFrame
import org.muzzzlo.player.core.designsystem.component.SectionTitle
import org.muzzzlo.player.core.designsystem.component.SwitchRow
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.model.LibraryFolder

/**
 * Library folders (no mockup, DESIGN.md, section 7): the switch of the mode "Only selected folders" (task 2.5.5), then
 * one switch row per folder with music. In that mode a folder taken in by a selected parent says so.
 */
@Composable
fun FoldersScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FoldersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScreenFrame(modifier) {
        SettingsHeader(onBack)
        SectionTitle(stringResource(R.string.settings_folders))
        if (!state.isLoaded) {
            Notice(stringResource(R.string.settings_loading))
            return@ScreenFrame
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.ContentGap)) {
            item(key = MODE_ROW) {
                SwitchRow(
                    text = stringResource(R.string.settings_folders_only_selected),
                    checked = state.onlySelected,
                    onCheckedChange = viewModel::onOnlySelectedChanged,
                    secondary = stringResource(R.string.settings_folders_only_selected_hint),
                )
            }
            if (state.selectionEmpty) {
                item(
                    key = SELECT_HINT,
                ) { Notice(stringResource(R.string.settings_folders_select_hint)) }
            }
            if (state.folders.isEmpty()) {
                item(
                    key = EMPTY_NOTICE,
                ) { Notice(stringResource(R.string.settings_folders_empty)) }
            }
            items(
                state.folders,
                key = { it.folder.relativePath },
            ) { item -> FolderRow(item, viewModel::onFolderToggled) }
        }
    }
}

@Composable
private fun FolderRow(
    item: FolderItem,
    onToggled: (LibraryFolder, Boolean) -> Unit,
) {
    val count = pluralStringResource(R.plurals.settings_folder_tracks, item.folder.trackCount, item.folder.trackCount)
    val root = stringResource(R.string.settings_folder_root)
    val parent = item.includedWith?.let { stringResource(R.string.settings_folder_included_with, it.ifEmpty { root }) }
    SwitchRow(
        text = item.folder.relativePath.ifEmpty { root },
        checked = item.included,
        onCheckedChange = { onToggled(item.folder, it) },
        secondary = listOfNotNull(count, parent).joinToString(" · "),
    )
}

private const val MODE_ROW = "mode"
private const val SELECT_HINT = "select-hint"
private const val EMPTY_NOTICE = "empty"
