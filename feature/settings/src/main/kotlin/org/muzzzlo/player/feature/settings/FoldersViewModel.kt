package org.muzzzlo.player.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.model.AppSettings
import org.muzzzlo.player.core.model.FolderSelection
import org.muzzzlo.player.core.model.LibraryFolder
import org.muzzzlo.player.core.model.LibraryMaintenance
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.SettingsRepository
import javax.inject.Inject

/**
 * A folder of the device and whether its music is in the library.
 *
 * @property includedWith in the mode "Only selected folders", the selected parent folder that takes this one in;
 *   such a folder cannot be left out alone. Null otherwise.
 */
data class FolderItem(
    val folder: LibraryFolder,
    val included: Boolean,
    val includedWith: String? = null,
)

/**
 * @property onlySelected the mode "Only selected folders" is on (task 2.5.5).
 * @property selectionEmpty the mode is on but nothing is selected yet, so every folder is still scanned.
 */
data class FoldersUiState(
    val isLoaded: Boolean = false,
    val onlySelected: Boolean = false,
    val selectionEmpty: Boolean = false,
    val folders: List<FolderItem> = emptyList(),
)

/**
 * Library folders (settings, "Library"): all folders with exclusions, or only the selected ones with their subfolders
 * ([FolderSelection], task 2.5.5). A change rescans, so the library follows at once.
 */
@HiltViewModel
class FoldersViewModel
    @Inject
    constructor(
        private val repository: SettingsRepository,
        private val library: LibraryRepository,
        private val maintenance: LibraryMaintenance,
    ) : ViewModel() {
        private val local = MutableStateFlow(FoldersUiState())
        val state: StateFlow<FoldersUiState> = local.asStateFlow()

        private var folders: List<LibraryFolder> = emptyList()
        private var settings = AppSettings()

        init {
            viewModelScope.launch {
                folders = maintenance.folders()
                repository.settings.collect {
                    settings = it
                    publish()
                }
            }
        }

        fun onOnlySelectedChanged(enabled: Boolean) = change { repository.setOnlySelectedFolders(enabled) }

        fun onFolderToggled(
            folder: LibraryFolder,
            included: Boolean,
        ) {
            val path = folder.relativePath
            if (!settings.onlySelectedFolders) {
                change { repository.setFolderIncluded(path, included) }
            } else if (FolderSelection.coveringParent(path, settings.selectedFolders) == null) {
                change { repository.setFolderSelected(path, included) }
            }
        }

        private fun change(save: suspend () -> Unit) {
            viewModelScope.launch {
                save()
                withContext(NonCancellable) { library.scan() }
            }
        }

        private fun publish() {
            val onlySelected = settings.onlySelectedFolders
            val listed = if (onlySelected) settings.selectedFolders else settings.excludedFolders
            // A folder of the list that MediaStore no longer lists stays visible, so it can be changed again.
            val known = folders.map { it.relativePath }.toSet()
            val all = folders + (listed - known).map { LibraryFolder(it, 0) }
            val items = all.sortedBy { it.relativePath.lowercase() }.map(::itemOf)
            local.update {
                FoldersUiState(
                    isLoaded = true,
                    onlySelected = onlySelected,
                    selectionEmpty = onlySelected && settings.selectedFolders.isEmpty(),
                    folders = items,
                )
            }
        }

        private fun itemOf(folder: LibraryFolder): FolderItem {
            val path = folder.relativePath
            if (!settings.onlySelectedFolders) return FolderItem(folder, path !in settings.excludedFolders)
            val parent = FolderSelection.coveringParent(path, settings.selectedFolders)
            return FolderItem(folder, parent != null || path in settings.selectedFolders, parent)
        }
    }
