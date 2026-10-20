package org.muzzzlo.player.core.model

/** The backup file is unreadable, not a backup, or written by a newer version of the app. */
class BackupFormatException(message: String) : Exception(message)

/** An artist spelling kept by the user (`artist_alias`); the artist is named, ids differ between devices. */
data class BackupAlias(
    val aliasKey: String,
    val aliasDisplay: String,
    val artistName: String,
)

/** A compound artist name rule of the user, or a built-in one that the user switched off (`artist_compound`). */
data class BackupCompound(
    val displayName: String,
    val matchKey: String,
    val builtinId: String?,
    val enabled: Boolean,
)

/**
 * Rating and favorite of a track. The track is found again by title, artist tags and length, because ids and
 * URIs differ between devices.
 *
 * @property artistTags the values of the artist tag as in the file (`track.artist_tag_values`).
 */
data class BackupTrackMark(
    val title: String,
    val artistTags: List<String>,
    val durationMs: Long,
    val ratingTenths: Int?,
    val favorite: Boolean,
)

/**
 * Everything the app keeps only in its own storage (ARCHITECTURE.md, section 8). Tags are not here, they live in
 * the files.
 */
data class BackupData(
    val createdAtMs: Long,
    val settings: AppSettings,
    val aliases: List<BackupAlias>,
    val compounds: List<BackupCompound>,
    val marks: List<BackupTrackMark>,
    val events: List<BackupEvent>,
)

/** A listen event without database ids; the track is found again like in [BackupTrackMark]. */
data class BackupEvent(
    val startedAtMs: Long,
    val playedMs: Long,
    val durationMs: Long,
    val counted: Boolean,
    val skipped: Boolean,
    val title: String,
    val artists: List<String>,
) {
    /** Two events with the same key are one listen; used to merge without duplicates. */
    val key: String get() = "$startedAtMs|$playedMs|$title"
}

/** Reads and writes the JSON backup. `schemaVersion` is [VERSION]; a newer file is refused, an older one is read. */
object BackupJson {
    const val VERSION = 1
    private const val FORMAT = "muzzzlo-backup"

    fun encode(data: BackupData): String =
        Json.write(
            mapOf(
                "format" to FORMAT,
                "schemaVersion" to VERSION,
                "createdAt" to data.createdAtMs,
                "settings" to settingsMap(data.settings),
                "aliases" to
                    data.aliases.map {
                        mapOf("key" to it.aliasKey, "display" to it.aliasDisplay, "artist" to it.artistName)
                    },
                "compounds" to
                    data.compounds.map {
                        mapOf(
                            "name" to it.displayName,
                            "matchKey" to it.matchKey,
                            "builtinId" to it.builtinId,
                            "enabled" to it.enabled,
                        )
                    },
                "marks" to
                    data.marks.map {
                        mapOf(
                            "title" to it.title,
                            "artists" to it.artistTags,
                            "durationMs" to it.durationMs,
                            "rating" to it.ratingTenths,
                            "favorite" to it.favorite,
                        )
                    },
                "events" to
                    data.events.map {
                        mapOf(
                            "startedAt" to it.startedAtMs,
                            "playedMs" to it.playedMs,
                            "durationMs" to it.durationMs,
                            "counted" to it.counted,
                            "skipped" to it.skipped,
                            "title" to it.title,
                            "artists" to it.artists,
                        )
                    },
            ),
            pretty = true,
        )

    /** Throws [BackupFormatException] for text that is not a backup of this or an older version. */
    fun decode(text: String): BackupData {
        val root =
            try {
                Json.parse(text) as? Map<*, *> ?: throw BackupFormatException("Not a backup file")
            } catch (e: JsonException) {
                throw BackupFormatException("Not a backup file: ${e.message}")
            }
        if (root["format"] != FORMAT) throw BackupFormatException("Not a backup file")
        val version = (root["schemaVersion"] as? Long) ?: throw BackupFormatException("No schemaVersion")
        if (version > VERSION) throw BackupFormatException("The backup is from a newer version ($version)")
        if (version < 1) throw BackupFormatException("Bad schemaVersion $version")
        return try {
            BackupData(
                createdAtMs = root.long("createdAt") ?: 0L,
                settings = settingsOf(root["settings"] as? Map<*, *> ?: emptyMap<String, Any?>()),
                aliases = root.items("aliases").map { BackupAlias(it.str("key"), it.str("display"), it.str("artist")) },
                compounds =
                    root.items("compounds").map {
                        BackupCompound(
                            it.str("name"),
                            it.str("matchKey"),
                            it["builtinId"] as? String,
                            it["enabled"] as? Boolean ?: true,
                        )
                    },
                marks =
                    root.items("marks").map {
                        BackupTrackMark(
                            it.str("title"),
                            it.strings("artists"),
                            it.long("durationMs") ?: 0L,
                            it.long("rating")?.toInt()?.coerceIn(0, MAX_RATING),
                            it["favorite"] as? Boolean ?: false,
                        )
                    },
                events =
                    root.items("events").map {
                        BackupEvent(
                            it.long("startedAt") ?: throw BackupFormatException("Event without startedAt"),
                            it.long("playedMs") ?: 0L,
                            it.long("durationMs") ?: 0L,
                            it["counted"] as? Boolean ?: false,
                            it["skipped"] as? Boolean ?: false,
                            it.str("title"),
                            it.strings("artists"),
                        )
                    },
            )
        } catch (e: ClassCastException) {
            throw BackupFormatException("Wrong shape of the backup")
        }
    }

    private const val MAX_RATING = 100

    private fun Map<*, *>.long(key: String): Long? = (this[key] as? Number)?.toLong()

    private fun Map<*, *>.str(key: String): String =
        this[key] as? String ?: throw BackupFormatException("Missing '$key'")

    private fun Map<*, *>.strings(key: String): List<String> =
        (this[key] as? List<*>).orEmpty().filterIsInstance<String>()

    private fun Map<*, *>.items(key: String): List<Map<*, *>> =
        (this[key] as? List<*>).orEmpty().map {
            it as? Map<*, *> ?: throw BackupFormatException("Wrong shape of '$key'")
        }

    private fun settingsMap(s: AppSettings): Map<String, Any?> =
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
        )

    /** A missing, unknown or out of range value reads as the default, like the settings repository does. */
    private fun settingsOf(m: Map<*, *>): AppSettings {
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
            replayGain =
                ReplayGainSettings(
                    mode = enumOf(m["replayGainMode"], d.replayGain.mode),
                    preampDb = (m["replayGainPreampDb"] as? Number)?.toFloat() ?: d.replayGain.preampDb,
                    untaggedGainDb = (m["replayGainUntaggedDb"] as? Number)?.toFloat() ?: d.replayGain.untaggedGainDb,
                    clipProtection = m["replayGainClipProtection"] as? Boolean ?: d.replayGain.clipProtection,
                ).clamped(),
            crossfade =
                CrossfadeSettings(
                    seconds = m.long("crossfadeSeconds")?.toInt() ?: d.crossfade.seconds,
                    withinAlbum = m["crossfadeWithinAlbum"] as? Boolean ?: d.crossfade.withinAlbum,
                ).clamped(),
            playbackBehavior =
                PlaybackBehaviorSettings(
                    transientFocus = enumOf(m["transientFocus"], d.playbackBehavior.transientFocus),
                    resumeOnHeadset = m["resumeOnHeadset"] as? Boolean ?: d.playbackBehavior.resumeOnHeadset,
                    skipSilence = m["skipSilence"] as? Boolean ?: d.playbackBehavior.skipSilence,
                ),
            stats =
                StatsSettings(
                    thresholdPercent = m.long("statsThresholdPercent")?.toInt() ?: d.stats.thresholdPercent,
                    thresholdMinutes = m.long("statsThresholdMinutes")?.toInt() ?: d.stats.thresholdMinutes,
                    privateMode = m["statsPrivateMode"] as? Boolean ?: d.stats.privateMode,
                ).clamped(),
        )
    }

    private inline fun <reified E : Enum<E>> enumOf(
        raw: Any?,
        default: E,
    ): E = enumValues<E>().firstOrNull { it.name == raw } ?: default
}
