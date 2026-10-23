package org.muzzzlo.player.core.model

internal fun Map<*, *>.long(key: String): Long? = (this[key] as? Number)?.toLong()

internal fun Map<*, *>.str(key: String): String = this[key] as? String ?: throw BackupFormatException("Missing '$key'")

internal fun Map<*, *>.strings(key: String): List<String> = (this[key] as? List<*>).orEmpty().filterIsInstance<String>()

internal fun Map<*, *>.items(key: String): List<Map<*, *>> =
    (this[key] as? List<*>).orEmpty().map {
        it as? Map<*, *> ?: throw BackupFormatException("Wrong shape of '$key'")
    }

/** Settings part of the backup JSON. */
internal object BackupSettingsJson {
    fun settingsMap(s: AppSettings): Map<String, Any?> =
        mapOf(
            "excludedFolders" to s.excludedFolders.sorted(),
            "minTrackSeconds" to s.minTrackSeconds,
            "id3v2VersionForNewTags" to s.id3v2VersionForNewTags,
            "confirmBeforeWrite" to s.confirmBeforeWrite,
            "language" to s.language.name,
            "dynamicAccent" to s.dynamicAccent,
            "gapless" to s.gapless,
            "replayGainMode" to s.replayGain.mode.name,
            "replayGainPreampDb" to s.replayGain.preampDb,
            "replayGainUntaggedDb" to s.replayGain.untaggedGainDb,
            "replayGainClipProtection" to s.replayGain.clipProtection,
            "crossfadeSeconds" to s.crossfade.seconds,
            "crossfadeWithinAlbum" to s.crossfade.withinAlbum,
            "transientFocus" to s.playbackBehavior.transientFocus.name,
            "resumeOnHeadset" to s.playbackBehavior.resumeOnHeadset,
            "skipSilence" to s.playbackBehavior.skipSilence,
            "statsThresholdPercent" to s.stats.thresholdPercent,
            "statsThresholdMinutes" to s.stats.thresholdMinutes,
            "statsPrivateMode" to s.stats.privateMode,
            "theme" to s.theme.name,
        )

    /** A missing, unknown or out of range value reads as the default, like the settings repository does. */
    fun settingsOf(m: Map<*, *>): AppSettings {
        val d = AppSettings()
        return AppSettings(
            excludedFolders = m.strings("excludedFolders").toSet(),
            minTrackSeconds =
                AppSettings.clampMinTrackSeconds(m.long("minTrackSeconds")?.toInt() ?: d.minTrackSeconds),
            id3v2VersionForNewTags =
                m.long("id3v2VersionForNewTags")?.toInt()?.takeIf {
                    it in AppSettings.ID3V2_VERSIONS
                } ?: d.id3v2VersionForNewTags,
            confirmBeforeWrite = m["confirmBeforeWrite"] as? Boolean ?: d.confirmBeforeWrite,
            language = enumOf(m["language"], d.language),
            dynamicAccent = m["dynamicAccent"] as? Boolean ?: d.dynamicAccent,
            gapless = m["gapless"] as? Boolean ?: d.gapless,
            replayGain = replayGainOf(m, d.replayGain),
            crossfade =
                CrossfadeSettings(
                    seconds = m.long("crossfadeSeconds")?.toInt() ?: d.crossfade.seconds,
                    withinAlbum = m["crossfadeWithinAlbum"] as? Boolean ?: d.crossfade.withinAlbum,
                ).clamped(),
            playbackBehavior = playbackBehaviorOf(m, d.playbackBehavior),
            stats =
                StatsSettings(
                    thresholdPercent = m.long("statsThresholdPercent")?.toInt() ?: d.stats.thresholdPercent,
                    thresholdMinutes = m.long("statsThresholdMinutes")?.toInt() ?: d.stats.thresholdMinutes,
                    privateMode = m["statsPrivateMode"] as? Boolean ?: d.stats.privateMode,
                ).clamped(),
            theme = enumOf(m["theme"], d.theme),
        )
    }

    private fun replayGainOf(
        m: Map<*, *>,
        d: ReplayGainSettings,
    ) = ReplayGainSettings(
        mode = enumOf(m["replayGainMode"], d.mode),
        preampDb = (m["replayGainPreampDb"] as? Number)?.toFloat() ?: d.preampDb,
        untaggedGainDb = (m["replayGainUntaggedDb"] as? Number)?.toFloat() ?: d.untaggedGainDb,
        clipProtection = m["replayGainClipProtection"] as? Boolean ?: d.clipProtection,
    ).clamped()

    private fun playbackBehaviorOf(
        m: Map<*, *>,
        d: PlaybackBehaviorSettings,
    ) = PlaybackBehaviorSettings(
        transientFocus = enumOf(m["transientFocus"], d.transientFocus),
        resumeOnHeadset = m["resumeOnHeadset"] as? Boolean ?: d.resumeOnHeadset,
        skipSilence = m["skipSilence"] as? Boolean ?: d.skipSilence,
    )

    private inline fun <reified E : Enum<E>> enumOf(
        raw: Any?,
        default: E,
    ): E = enumValues<E>().firstOrNull { it.name == raw } ?: default
}
