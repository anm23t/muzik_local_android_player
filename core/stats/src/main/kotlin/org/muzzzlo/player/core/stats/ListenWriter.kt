package org.muzzzlo.player.core.stats

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.muzzzlo.player.core.database.dao.ListenEventDao
import org.muzzzlo.player.core.database.entity.ListenEventEntity
import org.muzzzlo.player.core.model.FinishedListen
import org.muzzzlo.player.core.model.ListenEvent
import org.muzzzlo.player.core.model.ListenRules
import org.muzzzlo.player.core.model.StatsSettings

/**
 * Stores one listen: applies the threshold and the private mode ([ListenRules.eventOf]) with the settings at the
 * moment the listen ends and writes the event to `listen_event`.
 */
internal class ListenWriter(
    private val dao: ListenEventDao,
    private val settings: Flow<StatsSettings>,
) {
    /** Returns the stored event, or null when nothing was stored. */
    suspend fun write(listen: FinishedListen): ListenEvent? {
        val event = ListenRules.eventOf(listen, settings.first()) ?: return null
        val id = dao.insert(event.toEntity())
        return event.copy(id = id)
    }
}

internal fun ListenEvent.toEntity(): ListenEventEntity =
    ListenEventEntity(
        id = id,
        trackId = trackId,
        startedAt = startedAtMs,
        playedMs = playedMs,
        durationMs = durationMs,
        counted = counted,
        skipped = skipped,
        titleSnapshot = titleSnapshot,
        artistsSnapshot = jsonArray(artistsSnapshot),
        playlistId = playlistId,
    )

/** Encodes strings as a JSON array (`listen_event.artists_snapshot`), the same way as `track.artist_tag_values`. */
internal fun jsonArray(values: List<String>): String =
    values.joinToString(",", "[", "]") { value ->
        buildString {
            append('"')
            for (c in value) {
                when {
                    c == '"' -> append("\\\"")
                    c == '\\' -> append("\\\\")
                    c == '\n' -> append("\\n")
                    c == '\r' -> append("\\r")
                    c == '\t' -> append("\\t")
                    c < ' ' -> append("\\u%04x".format(c.code))
                    else -> append(c)
                }
            }
            append('"')
        }
    }
