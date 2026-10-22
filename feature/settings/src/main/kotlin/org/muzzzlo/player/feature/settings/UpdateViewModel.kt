package org.muzzzlo.player.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.muzzzlo.player.core.model.ReleaseInfo
import org.muzzzlo.player.core.model.UpdateCheckStore
import org.muzzzlo.player.core.model.UpdateChecker
import javax.inject.Inject

/** The window the app shows on top of its screens about updates, if any. */
sealed interface UpdatePrompt {
    data object None : UpdatePrompt

    /** The one question "Check for updates?" of the first start. */
    data object AskConsent : UpdatePrompt

    /** A newer release exists. */
    data class Available(val release: ReleaseInfo) : UpdatePrompt
}

/**
 * Starts the daily update check and holds its window (task 1.1.9). The question comes first, once; without a
 * "yes" the check never runs. Closing the window of a release marks it as told, so it is announced once.
 */
@HiltViewModel
class UpdateViewModel
    @Inject
    constructor(
        private val store: UpdateCheckStore,
        private val checker: UpdateChecker,
    ) : ViewModel() {
        private val local = MutableStateFlow<UpdatePrompt>(UpdatePrompt.None)
        val prompt: StateFlow<UpdatePrompt> = local.asStateFlow()

        init {
            viewModelScope.launch {
                if (store.state.first().enabled == null) local.value = UpdatePrompt.AskConsent else check()
            }
        }

        /** The answer to the question; closing the window without an answer counts as "no". */
        fun onConsent(agreed: Boolean) {
            local.value = UpdatePrompt.None
            viewModelScope.launch {
                store.setEnabled(agreed)
                if (agreed) check()
            }
        }

        /** The window of a release was closed or its link was opened. */
        fun onAnnouncementClosed() {
            val shown = local.value as? UpdatePrompt.Available ?: return
            local.value = UpdatePrompt.None
            viewModelScope.launch { store.setAnnounced(shown.release.tag) }
        }

        private suspend fun check() {
            checker.checkIfDue()?.let { local.value = UpdatePrompt.Available(it) }
        }
    }
