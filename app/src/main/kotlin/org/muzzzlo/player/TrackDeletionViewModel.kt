package org.muzzzlo.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.muzzzlo.player.core.model.DeleteRequest
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.PlayerController
import org.muzzzlo.player.core.model.TrackFileDeletion
import org.muzzzlo.player.core.model.WaveformRepository
import org.muzzzlo.player.core.model.queueIndicesToRemove
import javax.inject.Inject

/** Where the delete of a track file stands, as [TrackDeletionHost] shows it. */
sealed interface DeletionState {
    data object Idle : DeletionState

    /** Android 10 and older: the own warning that the file goes for good. */
    data class Confirm(val trackId: Long, val title: String) : DeletionState

    /** The system has to ask the user ([request] is a system dialog or a runtime permission). */
    data class AskSystem(val trackId: Long, val request: DeleteRequest) : DeletionState

    /** The file stayed; the screen says so once and calls [TrackDeletionViewModel.onFailureShown]. */
    data object Failed : DeletionState
}

/**
 * Deletes the file of a track from the menu of any screen (task 2.0.6). Android 11+ goes straight to the system
 * trash dialog; older versions warn first. After the file is gone the track is marked deleted (its row stays for
 * the trash, decision of the task), taken out of the queue, where the player moves on if it was playing, and its
 * cached waveform is dropped.
 */
@HiltViewModel
class TrackDeletionViewModel
    @Inject
    constructor(
        private val deletion: TrackFileDeletion,
        private val player: PlayerController,
        private val library: LibraryRepository,
        private val waveforms: WaveformRepository,
    ) : ViewModel() {
        private val local = MutableStateFlow<DeletionState>(DeletionState.Idle)
        val state: StateFlow<DeletionState> = local.asStateFlow()

        fun onDeleteRequested(
            trackId: Long,
            title: String,
        ) {
            if (deletion.method.isPermanent) {
                local.value = DeletionState.Confirm(trackId, title)
            } else {
                start(trackId)
            }
        }

        fun onConfirmed() {
            val confirm = local.value as? DeletionState.Confirm ?: return
            start(confirm.trackId)
        }

        fun onDismissed() {
            local.value = DeletionState.Idle
        }

        /** The answer of the system dialog or the permission request. */
        fun onSystemAnswer(allowed: Boolean) {
            val asked = local.value as? DeletionState.AskSystem ?: return
            local.value = DeletionState.Idle
            if (allowed) viewModelScope.launch { finish(asked.trackId) }
        }

        fun onFailureShown() {
            if (local.value == DeletionState.Failed) local.value = DeletionState.Idle
        }

        private fun start(trackId: Long) {
            local.value = DeletionState.Idle
            viewModelScope.launch {
                // The waveform key needs the file's stamps from MediaStore, so it is dropped while the file exists.
                library.tracksByIds(listOf(trackId)).firstOrNull()?.let { waveforms.forget(it) }
                when (val request = deletion.request(trackId)) {
                    DeleteRequest.NotNeeded -> finish(trackId)
                    DeleteRequest.Gone -> Unit
                    else -> local.value = DeletionState.AskSystem(trackId, request)
                }
            }
        }

        private suspend fun finish(trackId: Long) {
            if (!deletion.delete(trackId)) {
                local.value = DeletionState.Failed
                return
            }
            deletion.markDeleted(trackId)
            queueIndicesToRemove(player.state.value.queue, trackId).forEach(player::removeQueueItem)
        }
    }
