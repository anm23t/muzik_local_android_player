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
import org.muzzzlo.player.core.model.LibraryMaintenance
import org.muzzzlo.player.core.model.QuarantinedFile
import javax.inject.Inject

data class QuarantineUiState(
    val isLoaded: Boolean = false,
    val files: List<QuarantinedFile> = emptyList(),
    val retrying: String? = null,
)

/**
 * Files the tag reader crashed on. "Try again" takes a file off the list and reads it once more; if it crashes
 * again, the next start puts it back.
 */
@HiltViewModel
class QuarantineViewModel
    @Inject
    constructor(
        private val maintenance: LibraryMaintenance,
    ) : ViewModel() {
        private val local = MutableStateFlow(QuarantineUiState())
        val state: StateFlow<QuarantineUiState> = local.asStateFlow()

        init {
            refresh()
        }

        fun onRetryClicked(file: QuarantinedFile) {
            if (local.value.retrying != null) return
            local.update { it.copy(retrying = file.uri) }
            viewModelScope.launch {
                withContext(NonCancellable) { maintenance.retryQuarantined(file.uri) }
                local.update { it.copy(retrying = null) }
                refresh()
            }
        }

        private fun refresh() {
            viewModelScope.launch {
                val files = maintenance.quarantined()
                local.update { it.copy(isLoaded = true, files = files) }
            }
        }
    }
