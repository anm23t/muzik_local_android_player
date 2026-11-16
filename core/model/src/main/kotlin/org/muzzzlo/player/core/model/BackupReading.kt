package org.muzzzlo.player.core.model

internal fun Map<*, *>.long(key: String): Long? = (this[key] as? Number)?.toLong()

internal fun Map<*, *>.str(key: String): String = this[key] as? String ?: throw BackupFormatException("Missing '$key'")

internal fun Map<*, *>.bool(
    key: String,
    default: Boolean,
): Boolean = this[key] as? Boolean ?: default

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
            "onlySelectedFolders" to s.onlySelectedFolders,
            "selectedFolders" to s.selectedFolders.sorted(),
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
            "waveform" to s.waveform,
            "animatedBackground" to s.animatedBackground,
            "folderCovers" to s.folderCovers,
            "equalizerEnabled" to s.equalizer.enabled,
            "equalizerPreset" to s.equalizer.preset.name,
            "equalizerCustomDb" to s.equalizer.customDb,
            "equalizerPreampDb" to s.equalizer.preampDb,
        ) + sortsMap(s.listSorts)

    private fun sortsMap(sorts: Map<SortedList, StoredSort>): Map<String, Any?> =
        sorts.entries.flatMap { (list, sort) ->
            listOf("${list.key}SortField" to sort.field, "${list.key}SortAscending" to sort.ascending)
        }.toMap()

    private fun sortsOf(m: Map<*, *>): Map<SortedList, StoredSort> =
        SortedList.entries.mapNotNull { list ->
            val field = m["${list.key}SortField"] as? String ?: return@mapNotNull null
            val ascending = m["${list.key}SortAscending"] as? Boolean ?: return@mapNotNull null
            list to StoredSort(field, ascending)
        }.toMap()

    private val SortedList.key: String get() = name.lowercase()

    /** A missing, unknown or out of range value reads as the default, like the settings repository does. */
    fun settingsOf(m: Map<*, *>): AppSettings {
        val d = AppSettings()
        return AppSettings(
            excludedFolders = m.strings("excludedFolders").toSet(),
            onlySelectedFolders = m.bool("onlySelectedFolders", d.onlySelectedFolders),
            selectedFolders = m.strings("selectedFolders").toSet(),
            minTrackSeconds =
                AppSettings.clampMinTrackSeconds(m.long("minTrackSeconds")?.toInt() ?: d.minTrackSeconds),
            id3v2VersionForNewTags =
                m.long("id3v2VersionForNewTags")?.toInt()?.takeIf {
                    it in AppSettings.ID3V2_VERSIONS
                } ?: d.id3v2VersionForNewTags,
            confirmBeforeWrite = m.bool("confirmBeforeWrite", d.confirmBeforeWrite),
            language = enumOf(m["language"], d.language),
            dynamicAccent = m.bool("dynamicAccent", d.dynamicAccent),
            gapless = m.bool("gapless", d.gapless),
            replayGain = replayGainOf(m, d.replayGain),
            crossfade =
                CrossfadeSettings(
                    seconds = m.long("crossfadeSeconds")?.toInt() ?: d.crossfade.seconds,
                    withinAlbum = m.bool("crossfadeWithinAlbum", d.crossfade.withinAlbum),
                ).clamped(),
            playbackBehavior = playbackBehaviorOf(m, d.playbackBehavior),
            stats =
                StatsSettings(
                    thresholdPercent = m.long("statsThresholdPercent")?.toInt() ?: d.stats.thresholdPercent,
                    thresholdMinutes = m.long("statsThresholdMinutes")?.toInt() ?: d.stats.thresholdMinutes,
                    privateMode = m.bool("statsPrivateMode", d.stats.privateMode),
                ).clamped(),
            theme = enumOf(m["theme"], d.theme),
            waveform = m.bool("waveform", d.waveform),
            equalizer = equalizerOf(m, d.equalizer),
            listSorts = sortsOf(m),
            animatedBackground = m.bool("animatedBackground", d.animatedBackground),
            folderCovers = m.bool("folderCovers", d.folderCovers),
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

    private fun equalizerOf(
        m: Map<*, *>,
        d: EqualizerSettings,
    ) = EqualizerSettings(
        enabled = m["equalizerEnabled"] as? Boolean ?: d.enabled,
        preset = enumOf(m["equalizerPreset"], d.preset),
        customDb = (m["equalizerCustomDb"] as? List<*>)?.map { (it as? Number)?.toFloat() ?: 0f } ?: d.customDb,
        preampDb = (m["equalizerPreampDb"] as? Number)?.toFloat() ?: d.preampDb,
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
