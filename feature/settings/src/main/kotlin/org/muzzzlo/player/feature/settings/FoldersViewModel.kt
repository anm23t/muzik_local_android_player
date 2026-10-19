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
import org.muzzzlo.player.core.model.LibraryFolder
import org.muzzzlo.player.core.model.LibraryMaintenance
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.SettingsRepository
import javax.inject.Inject

/** A folder of the device and whether its music is in the library. */
data class FolderItem(
    val folder: LibraryFolder,
    val included: Boolean,
)

data class FoldersUiState(
    val isLoaded: Boolean = false,
    val folders: List<FolderItem> = emptyList(),
)

/** Include or exclude folders (settings, "Library"). A change rescans, so the library follows at once. */
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
        private var excluded: Set<String> = emptySet()

        init {
            viewModelScope.launch {
                folders = maintenance.folders()
                repository.settings.collect {
                    excluded = it.excludedFolders
                    publish()
                }
            }
        }

        fun onFolderToggled(
            folder: LibraryFolder,
            included: Boolean,
        ) {
            viewModelScope.launch {
                repository.setFolderIncluded(folder.relativePath, included)
                withContext(NonCancellable) { library.scan() }
            }
        }

        private fun publish() {
            // An excluded folder that MediaStore no longer lists stays visible, so it can be included again.
            val known = folders.map { it.relativePath }.toSet()
            val all = folders + (excluded - known).map { LibraryFolder(it, 0) }
            val items =
                all.sortedBy { it.relativePath.lowercase() }.map {
                    FolderItem(
                        it,
                        it.relativePath !in excluded,
                    )
                }
            local.update { FoldersUiState(isLoaded = true, folders = items) }
        }
    }
