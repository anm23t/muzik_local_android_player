package org.muzzzlo.player.feature.player

import kotlinx.coroutines.flow.MutableStateFlow
import org.muzzzlo.player.core.model.AppLanguage
import org.muzzzlo.player.core.model.AppSettings
import org.muzzzlo.player.core.model.CrossfadeSettings
import org.muzzzlo.player.core.model.InterfaceTheme
import org.muzzzlo.player.core.model.PlaybackBehaviorSettings
import org.muzzzlo.player.core.model.ReplayGainSettings
import org.muzzzlo.player.core.model.SettingsRepository
import org.muzzzlo.player.core.model.StatsSettings

/** Settings in memory; only the waveform switch matters to the player. */
class FakeSettings : SettingsRepository {
    override val settings = MutableStateFlow(AppSettings())

    override suspend fun setWaveform(enabled: Boolean) {
        settings.value = settings.value.copy(waveform = enabled)
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
}
