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

/** Library folders (no mockup, DESIGN.md, section 7): one switch row per folder with music. */
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
        when {
            !state.isLoaded -> Notice(stringResource(R.string.settings_loading))
            state.folders.isEmpty() -> Notice(stringResource(R.string.settings_folders_empty))
            else ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.ContentGap)) {
                    items(state.folders, key = { it.folder.relativePath }) { item ->
                        SwitchRow(
                            text = item.folder.relativePath.ifEmpty { stringResource(R.string.settings_folder_root) },
                            checked = item.included,
                            onCheckedChange = { viewModel.onFolderToggled(item.folder, it) },
                            secondary =
                                pluralStringResource(
                                    R.plurals.settings_folder_tracks,
                                    item.folder.trackCount,
                                    item.folder.trackCount,
                                ),
                        )
                    }
                }
        }
    }
}
