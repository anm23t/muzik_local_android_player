package org.muzzzlo.player.feature.stats

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.muzzzlo.player.core.model.BackgroundPaletteRepository
import org.muzzzlo.player.core.model.CalendarPeriod
import org.muzzzlo.player.core.model.PeriodSummary
import org.muzzzlo.player.core.model.SettingsRepository
import org.muzzzlo.player.core.model.SummaryRepository
import javax.inject.Inject

/** Navigation argument of the summary slides: [CalendarPeriod.key]. */
const val SUMMARY_PERIOD_ARG = "summaryPeriod"

/**
 * What the summary slides render (task 2.5.12).
 *
 * @property summary null until loaded, for an unknown period and for a period without listens.
 * @property palette the colors of the live background: the cover of the top track, or the default red when the
 *   dynamic accent is off or the top track is gone.
 * @property sharing the picture of a slide is being made; "Share" waits.
 * @property shareUri the picture to hand to the share sheet; the screen opens it once and calls back.
 */
data class SummaryUiState(
    val period: CalendarPeriod? = null,
    val summary: PeriodSummary? = null,
    val slides: List<SummarySlide> = emptyList(),
    val palette: List<Int> = emptyList(),
    val isLoaded: Boolean = false,
    val sharing: Boolean = false,
    val shareUri: String? = null,
)

/** The slides of one period summary; the summary is computed once when the screen opens. */
@HiltViewModel
class SummaryViewModel
    @Inject
    constructor(
        private val summaries: SummaryRepository,
        private val palettes: BackgroundPaletteRepository,
        private val settings: SettingsRepository,
        private val images: SlideImageWriter,
        savedState: SavedStateHandle,
    ) : ViewModel() {
        private val period = CalendarPeriod.fromKey(savedState[SUMMARY_PERIOD_ARG])
        private val mutableState = MutableStateFlow(SummaryUiState(period))
        val state: StateFlow<SummaryUiState> = mutableState.asStateFlow()

        init {
            viewModelScope.launch { mutableState.value = load() }
        }

        /** "Share" on [slide], captured as [image]: makes the picture and hands it to the screen. */
        fun onShare(
            slide: SummarySlide,
            image: ImageBitmap,
        ) {
            val current = mutableState.value
            val period = current.period ?: return
            if (current.sharing) return
            mutableState.update { it.copy(sharing = true) }
            viewModelScope.launch {
                // A picture that cannot be made or written is no reason to close the app: "Share" just works again.
                val uri =
                    runCatching { images.write(image, current.palette, ShareImage.fileName(period, slide)) }
                        .onFailure { if (it is CancellationException) throw it }
                        .getOrNull()
                mutableState.update { it.copy(sharing = false, shareUri = uri) }
            }
        }

        fun onShareOpened() = mutableState.update { it.copy(shareUri = null) }

        private suspend fun load(): SummaryUiState {
            val summary = period?.let { summaries.summary(it) } ?: return SummaryUiState(period, isLoaded = true)
            val dynamic = settings.settings.first().dynamicAccent
            val trackId = summary.topTrack?.trackId
            val palette = if (dynamic && trackId != null) palettes.paletteOf(trackId) else palettes.defaultPalette
            return SummaryUiState(period, summary, slidesOf(summary), palette, isLoaded = true)
        }
    }
