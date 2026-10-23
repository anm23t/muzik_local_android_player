package org.muzzzlo.player.core.model

import kotlinx.coroutines.flow.Flow

/** Interface language, independent of the system language unless [SYSTEM]. */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    RUSSIAN("ru"),
}

/**
 * How screens are framed (task 1.2.6): [WINDOW] is the rounded window with borders of the mockup, [HUGE] drops the
 * frame and the inner borders and keeps the content 12 dp from the screen edges.
 */
enum class InterfaceTheme {
    WINDOW,
    HUGE,
}

/**
 * The settings of the app (ARCHITECTURE.md, section 9). The default values live here, in one place; a later task
 * adds its own fields with defaults.
 *
 * @property excludedFolders MediaStore `RELATIVE_PATH` values (for example `Music/Podcasts/`) of folders that stay
 *   out of the library; matched exactly, a subfolder is its own entry. Empty: every folder is included.
 * @property minTrackSeconds shorter files are left out of the library; 0 keeps everything.
 * @property id3v2VersionForNewTags 3 or 4: the ID3v2 version for an MP3 file that has no ID3v2 tag yet.
 * @property confirmBeforeWrite show the "was, will be" dialog before the tag editor writes one track; the album
 *   and artist editors always ask.
 * @property dynamicAccent the accent follows the cover of the playing track; off means always the default red.
 * @property gapless the encoder delay and padding of MP3, M4A and similar files are cut off, so albums play without
 *   a pause between tracks; off for files where this causes clicks or cut-off sound.
 * @property replayGain loudness levelling by ReplayGain tags.
 * @property crossfade overlap of the end of a track with the start of the next one.
 * @property playbackBehavior audio focus, headset and silence handling.
 * @property stats the listen threshold and the private mode of the statistics.
 * @property theme framing of the screens.
 * @property waveform the big player shows the waveform of the track instead of the plain progress bar.
 */
data class AppSettings(
    val excludedFolders: Set<String> = emptySet(),
    val minTrackSeconds: Int = DEFAULT_MIN_TRACK_SECONDS,
    val id3v2VersionForNewTags: Int = TagWriter.DEFAULT_ID3V2_VERSION,
    val confirmBeforeWrite: Boolean = true,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val dynamicAccent: Boolean = true,
    val gapless: Boolean = true,
    val replayGain: ReplayGainSettings = ReplayGainSettings(),
    val crossfade: CrossfadeSettings = CrossfadeSettings(),
    val playbackBehavior: PlaybackBehaviorSettings = PlaybackBehaviorSettings(),
    val stats: StatsSettings = StatsSettings(),
    val theme: InterfaceTheme = InterfaceTheme.WINDOW,
    val waveform: Boolean = true,
) {
    companion object {
        const val DEFAULT_MIN_TRACK_SECONDS = 30
        const val MAX_MIN_TRACK_SECONDS = 600

        /** The version options the user can choose from. */
        val ID3V2_VERSIONS = listOf(3, 4)

        /** Brings [seconds] into the allowed range. */
        fun clampMinTrackSeconds(seconds: Int): Int = seconds.coerceIn(0, MAX_MIN_TRACK_SECONDS)
    }
}

/** Reads and changes [AppSettings]; stored in DataStore, implemented in `feature:settings`. */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setFolderIncluded(
        relativePath: String,
        included: Boolean,
    )

    suspend fun setMinTrackSeconds(seconds: Int)

    suspend fun setId3v2VersionForNewTags(version: Int)

    suspend fun setConfirmBeforeWrite(enabled: Boolean)

    suspend fun setLanguage(language: AppLanguage)

    suspend fun setDynamicAccent(enabled: Boolean)

    suspend fun setGapless(enabled: Boolean)

    /** Stores [settings] with their decibel values clamped ([ReplayGainSettings.clamped]). */
    suspend fun setReplayGain(settings: ReplayGainSettings)

    /** Stores [settings] with the length clamped ([CrossfadeSettings.clamped]). */
    suspend fun setCrossfade(settings: CrossfadeSettings)

    suspend fun setPlaybackBehavior(settings: PlaybackBehaviorSettings)

    /** Stores [settings] with the threshold clamped ([StatsSettings.clamped]). */
    suspend fun setStats(settings: StatsSettings)

    suspend fun setTheme(theme: InterfaceTheme)

    suspend fun setWaveform(enabled: Boolean)
}
