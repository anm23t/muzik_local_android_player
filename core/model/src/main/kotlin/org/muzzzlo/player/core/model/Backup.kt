package org.muzzzlo.player.core.model

/** The backup file is unreadable, not a backup, or written by a newer version of the app. */
class BackupFormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

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
    /** When the track became a favorite (epoch ms), null when unknown or for older backups. */
    val favoritedAtMs: Long? = null,
)

/** A track of a backed up playlist, found again like in [BackupTrackMark]. */
data class BackupPlaylistTrack(
    val title: String,
    val artistTags: List<String>,
    val durationMs: Long,
)

/**
 * A playlist with its tracks in order (schema version 2). [createdAtMs] with [name] tells whether the playlist is
 * already there on import.
 */
data class BackupPlaylist(
    val name: String,
    val createdAtMs: Long,
    val lastPlayedAtMs: Long?,
    val tracks: List<BackupPlaylistTrack>,
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
    val playlists: List<BackupPlaylist> = emptyList(),
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

/**
 * Reads and writes the JSON backup. `schemaVersion` is [VERSION]; a newer file is refused, an older one is read.
 * Version 2 (task 1.1.5) added `playlists`; a version 1 file reads with none.
 */
object BackupJson {
    const val VERSION = 2
    private const val FORMAT = "muzzzlo-backup"

    fun encode(data: BackupData): String =
        Json.write(
            mapOf(
                "format" to FORMAT,
                "schemaVersion" to VERSION,
                "createdAt" to data.createdAtMs,
                "settings" to BackupSettingsJson.settingsMap(data.settings),
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
                            "favoritedAt" to it.favoritedAtMs,
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
                "playlists" to data.playlists.map(::playlistMap),
            ),
            pretty = true,
        )

    /** Throws [BackupFormatException] for text that is not a backup of this or an older version. */
    fun decode(text: String): BackupData {
        val root = parseRoot(text)
        checkHeader(root)
        return try {
            dataOf(root)
        } catch (e: ClassCastException) {
            throw BackupFormatException("Wrong shape of the backup", e)
        }
    }

    private fun parseRoot(text: String): Map<*, *> =
        try {
            Json.parse(text) as? Map<*, *> ?: throw BackupFormatException("Not a backup file")
        } catch (e: JsonException) {
            throw BackupFormatException("Not a backup file: ${e.message}", e)
        }

    private fun checkHeader(root: Map<*, *>) {
        val version = root["schemaVersion"] as? Long
        val problem =
            when {
                root["format"] != FORMAT -> "Not a backup file"
                version == null -> "No schemaVersion"
                version > VERSION -> "The backup is from a newer version ($version)"
                version < 1 -> "Bad schemaVersion $version"
                else -> null
            }
        if (problem != null) throw BackupFormatException(problem)
    }

    private fun dataOf(root: Map<*, *>): BackupData =
        BackupData(
            createdAtMs = root.long("createdAt") ?: 0L,
            settings = BackupSettingsJson.settingsOf(root["settings"] as? Map<*, *> ?: emptyMap<String, Any?>()),
            aliases = root.items("aliases").map { BackupAlias(it.str("key"), it.str("display"), it.str("artist")) },
            compounds = root.items("compounds").map(::compoundOf),
            marks = root.items("marks").map(::markOf),
            events = root.items("events").map(::eventOf),
            playlists = root.items("playlists").map(::playlistOf),
        )

    private fun playlistMap(it: BackupPlaylist) =
        mapOf(
            "name" to it.name,
            "createdAt" to it.createdAtMs,
            "lastPlayedAt" to it.lastPlayedAtMs,
            "tracks" to
                it.tracks.map { track ->
                    mapOf("title" to track.title, "artists" to track.artistTags, "durationMs" to track.durationMs)
                },
        )

    private fun playlistOf(it: Map<*, *>) =
        BackupPlaylist(
            it.str("name"),
            it.long("createdAt") ?: 0L,
            it.long("lastPlayedAt"),
            it.items("tracks").map { track ->
                BackupPlaylistTrack(track.str("title"), track.strings("artists"), track.long("durationMs") ?: 0L)
            },
        )

    private fun compoundOf(it: Map<*, *>) =
        BackupCompound(
            it.str("name"),
            it.str("matchKey"),
            it["builtinId"] as? String,
            it["enabled"] as? Boolean ?: true,
        )

    private fun markOf(it: Map<*, *>) =
        BackupTrackMark(
            it.str("title"),
            it.strings("artists"),
            it.long("durationMs") ?: 0L,
            it.long("rating")?.toInt()?.coerceIn(0, MAX_RATING),
            it["favorite"] as? Boolean ?: false,
            it.long("favoritedAt"),
        )

    private fun eventOf(it: Map<*, *>) =
        BackupEvent(
            it.long("startedAt") ?: throw BackupFormatException("Event without startedAt"),
            it.long("playedMs") ?: 0L,
            it.long("durationMs") ?: 0L,
            it["counted"] as? Boolean ?: false,
            it["skipped"] as? Boolean ?: false,
            it.str("title"),
            it.strings("artists"),
        )

    private const val MAX_RATING = 100
}
