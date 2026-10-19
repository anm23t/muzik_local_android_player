package org.muzzzlo.player.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.muzzzlo.player.core.model.CompoundRepository
import org.muzzzlo.player.core.model.CompoundRule
import org.muzzzlo.player.core.model.CompoundRules
import javax.inject.Inject

/** Why the typed name was not added. */
enum class CompoundInputError { TooShort, Duplicate }

/**
 * A removal that splits artists again and waits for the user's yes.
 *
 * @property affectedTracks tracks whose artists change when the rule goes.
 */
data class RemoveConfirm(
    val rule: CompoundRule,
    val affectedTracks: Int,
)

/** What the compound names screen renders. */
data class CompoundNamesUiState(
    val rules: List<CompoundRule> = emptyList(),
    val isLoaded: Boolean = false,
    val input: String = "",
    val inputError: CompoundInputError? = null,
    val confirm: RemoveConfirm? = null,
    val isBusy: Boolean = false,
)

/**
 * The list of compound artist names (no mockup, DESIGN.md, section 7): the user's rules and the built-in ones.
 * A rule of the user is deleted, a built-in one is switched off and on. Before a removal that would split artists
 * again the screen asks, with the number of tracks it touches. Files are never changed.
 */
@HiltViewModel
class CompoundNamesViewModel
    @Inject
    constructor(
        private val compounds: CompoundRepository,
    ) : ViewModel() {
        private val local = MutableStateFlow(CompoundNamesUiState())

        val state: StateFlow<CompoundNamesUiState> =
            combine(compounds.observeRules(), local) { rules, local ->
                local.copy(rules = rules, isLoaded = true)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CompoundNamesUiState())

        fun onInputChanged(text: String) = local.update { it.copy(input = text, inputError = null) }

        /** Adds the typed name as a rule of the user. */
        fun onAddClicked() {
            val name = local.value.input.trim()
            if (local.value.isBusy) return
            if (CompoundRules.fragments(name).size < 2) {
                local.update { it.copy(inputError = CompoundInputError.TooShort) }
                return
            }
            launchBusy {
                if (compounds.remember(name) < 0) {
                    local.update { it.copy(inputError = CompoundInputError.Duplicate) }
                } else {
                    local.update { it.copy(input = "", inputError = null) }
                }
            }
        }

        /** Delete (a rule of the user) or switch off (a built-in one); asks first when artists would split again. */
        fun onRemoveClicked(rule: CompoundRule) {
            if (local.value.isBusy) return
            launchBusy {
                val affected = compounds.countAffectedBy(rule.id)
                if (affected > 0) {
                    local.update { it.copy(confirm = RemoveConfirm(rule, affected)) }
                } else {
                    compounds.remove(rule.id)
                }
            }
        }

        fun onRemoveConfirmed() {
            val rule = local.value.confirm?.rule ?: return
            local.update { it.copy(confirm = null) }
            launchBusy { compounds.remove(rule.id) }
        }

        fun onRemoveDismissed() = local.update { it.copy(confirm = null) }

        /** Switches a built-in rule on again. */
        fun onEnableClicked(rule: CompoundRule) {
            if (local.value.isBusy) return
            launchBusy { compounds.enable(rule.id) }
        }

        private fun launchBusy(block: suspend () -> Unit) {
            local.update { it.copy(isBusy = true) }
            viewModelScope.launch {
                try {
                    block()
                } finally {
                    local.update { it.copy(isBusy = false) }
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
