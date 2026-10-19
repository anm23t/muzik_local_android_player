package org.muzzzlo.player.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.muzzzlo.player.core.model.ArtistCheck
import org.muzzzlo.player.core.model.ArtistCheckRepository
import org.muzzzlo.player.core.model.ArtistVariant
import org.muzzzlo.player.core.model.LibraryRepository
import org.muzzzlo.player.core.model.VariantGroup
import javax.inject.Inject

/**
 * A group of spellings in the check screen.
 *
 * @property keepId the artist whose spelling stays; the others are folded into it.
 */
data class CheckGroup(
    val group: VariantGroup,
    val keepId: Long = group.suggested.artistId,
)

/** What the check screen renders. [groups] is empty and [isLoaded] true when nothing looks doubled. */
data class ArtistCheckUiState(
    val groups: List<CheckGroup> = emptyList(),
    val isLoaded: Boolean = false,
    val isApplying: Boolean = false,
    val appliedCount: Int = 0,
)

/**
 * The "Check" screen of the artist list (no mockup, DESIGN.md, section 7): groups of artists whose names differ
 * only in case, accents or punctuation, with the most frequent spelling preselected. Applying a group folds the
 * others into the chosen artist in the database and remembers their spellings as aliases. Files stay as they are.
 * Skipping a group only hides it until the next check.
 */
@HiltViewModel
class ArtistCheckViewModel
    @Inject
    constructor(
        private val library: LibraryRepository,
        private val check: ArtistCheckRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(ArtistCheckUiState())
        val state: StateFlow<ArtistCheckUiState> = mutableState.asStateFlow()

        init {
            viewModelScope.launch { load() }
        }

        private suspend fun load() {
            val artists = library.observeArtists().first().map { ArtistVariant(it.id, it.name, it.trackCount) }
            val groups = ArtistCheck.groups(artists).map { CheckGroup(it) }
            mutableState.update { it.copy(groups = groups, isLoaded = true) }
        }

        /** The user picked which spelling of the group at [index] stays. */
        fun onKeepChosen(
            index: Int,
            artistId: Long,
        ) = mutableState.update { state ->
            state.copy(groups = state.groups.mapIndexed { i, g -> if (i == index) g.copy(keepId = artistId) else g })
        }

        fun onSkip(index: Int) =
            mutableState.update {
                it.copy(
                    groups = it.groups.filterIndexed { i, _ -> i != index },
                )
            }

        /** Folds the group at [index] into its chosen spelling. */
        fun onApply(index: Int) {
            val current = mutableState.value
            val group = current.groups.getOrNull(index) ?: return
            if (current.isApplying) return
            mutableState.update { it.copy(isApplying = true) }
            viewModelScope.launch {
                val others = group.group.variants.map { it.artistId }.filter { it != group.keepId }
                try {
                    check.merge(group.keepId, others)
                    mutableState.update { state ->
                        state.copy(
                            groups = state.groups.filter { it !== group },
                            appliedCount = state.appliedCount + 1,
                        )
                    }
                } finally {
                    mutableState.update { it.copy(isApplying = false) }
                }
            }
        }
    }
