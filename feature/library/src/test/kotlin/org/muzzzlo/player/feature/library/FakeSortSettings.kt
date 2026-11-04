package org.muzzzlo.player.feature.library

import kotlinx.coroutines.flow.MutableStateFlow
import org.muzzzlo.player.core.model.AppLanguage
import org.muzzzlo.player.core.model.AppSettings
import org.muzzzlo.player.core.model.CrossfadeSettings
import org.muzzzlo.player.core.model.EqualizerSettings
import org.muzzzlo.player.core.model.InterfaceTheme
import org.muzzzlo.player.core.model.PlaybackBehaviorSettings
import org.muzzzlo.player.core.model.ReplayGainSettings
import org.muzzzlo.player.core.model.SettingsRepository
import org.muzzzlo.player.core.model.SortedList
import org.muzzzlo.player.core.model.StatsSettings
import org.muzzzlo.player.core.model.StoredSort

/** Keeps only what the list screens use, the remembered sorts; the other setters do nothing. */
class FakeSortSettings(sorts: Map<SortedList, StoredSort> = emptyMap()) : SettingsRepository {
    private val state = MutableStateFlow(AppSettings(listSorts = sorts))
    override val settings = state

    override suspend fun setListSort(
        list: SortedList,
        sort: StoredSort,
    ) {
        state.value = state.value.copy(listSorts = state.value.listSorts + (list to sort))
    }

    override suspend fun setFolderIncluded(
        relativePath: String,
        included: Boolean,
    ) = Unit

    override suspend fun setMinTrackSeconds(seconds: Int) = Unit

    override suspend fun setId3v2VersionForNewTags(version: Int) = Unit

    override suspend fun setConfirmBeforeWrite(enabled: Boolean) = Unit

    override suspend fun setLanguage(language: AppLanguage) = Unit

    override suspend fun setDynamicAccent(enabled: Boolean) = Unit

    override suspend fun setGapless(enabled: Boolean) = Unit

    override suspend fun setReplayGain(settings: ReplayGainSettings) = Unit

    override suspend fun setCrossfade(settings: CrossfadeSettings) = Unit

    override suspend fun setPlaybackBehavior(settings: PlaybackBehaviorSettings) = Unit

    override suspend fun setStats(settings: StatsSettings) = Unit

    override suspend fun setTheme(theme: InterfaceTheme) = Unit

    override suspend fun setWaveform(enabled: Boolean) = Unit

    override suspend fun setFolderCovers(enabled: Boolean) = Unit

    override suspend fun setEqualizer(settings: EqualizerSettings) = Unit
}
