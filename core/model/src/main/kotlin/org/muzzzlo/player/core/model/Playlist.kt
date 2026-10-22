package org.muzzzlo.player.core.model

import kotlinx.coroutines.flow.Flow

/**
 * A playlist of the user. It lives only in the database and in the backup (CLAUDE.md, decision 4).
 *
 * @property trackCount tracks whose files are on the device; entries of missing files are not counted.
 * @property firstTrackId the first such track, its cover stands for the playlist; null for an empty playlist.
 * @property lastPlayedAtMs when playback last started from the playlist, null before the first time.
 */
data class Playlist(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val firstTrackId: Long?,
    val createdAtMs: Long,
    val lastPlayedAtMs: Long?,
)

/** A track in a playlist. The same track may be there twice, so an entry has its own [entryId]. */
data class PlaylistEntry(
    val entryId: Long,
    val track: Track,
)

/** Playlists of the user and their tracks. Entries of missing files are hidden and come back with the file. */
interface PlaylistRepository {
    /** All playlists in name order. */
    fun observePlaylists(): Flow<List<Playlist>>

    /** The playlist, or null once it is deleted. */
    fun observePlaylist(playlistId: Long): Flow<Playlist?>

    /** Visible entries of the playlist in order. */
    fun observeEntries(playlistId: Long): Flow<List<PlaylistEntry>>

    /** Creates an empty playlist; [name] must be valid ([PlaylistNames.clean]). Returns its id. */
    suspend fun create(name: String): Long

    suspend fun rename(
        playlistId: Long,
        name: String,
    )

    suspend fun delete(playlistId: Long)

    /** Adds the tracks to the end of the playlist. */
    suspend fun addTracks(
        playlistId: Long,
        trackIds: List<Long>,
    )

    suspend fun removeEntry(entryId: Long)

    /**
     * Puts the visible entries in the order of [entryIds] (all visible entries of the playlist).
     * Hidden entries keep their places ([PlaylistOrder.reorder]).
     */
    suspend fun reorder(
        playlistId: Long,
        entryIds: List<Long>,
    )

    /** Remembers that playback started from the playlist now, for "Continue listening". */
    suspend fun markPlayed(playlistId: Long)
}

/** Rules for playlist names. */
object PlaylistNames {
    const val MAX_LENGTH = 100

    /**
     * The name to store: trimmed, with runs of whitespace and line breaks turned into one space, cut to
     * [MAX_LENGTH] characters. Null when nothing is left. Names do not have to be unique.
     */
    fun clean(name: String): String? =
        name.trim().replace(WHITESPACE, " ").take(MAX_LENGTH).trimEnd().takeIf { it.isNotEmpty() }

    private val WHITESPACE = Regex("\\s+")
}

/** Order of playlist entries when some of them are hidden (their files are missing). */
object PlaylistOrder {
    /** An entry of the playlist in stored order. */
    data class Slot(
        val entryId: Long,
        val visible: Boolean,
    )

    /**
     * The full new order of entry ids. Hidden entries stay in their slots; the visible slots get [visibleOrder]
     * in turn. Ids in [visibleOrder] that are not visible entries are ignored, visible entries missing from it keep
     * their relative order after the given ones, so a stale list from the screen never loses an entry.
     */
    fun reorder(
        slots: List<Slot>,
        visibleOrder: List<Long>,
    ): List<Long> {
        val visible = slots.filter { it.visible }.map { it.entryId }
        val known = visible.toSet()
        val requested = visibleOrder.distinct().filter { it in known }
        val queue = ArrayDeque(requested + (visible - requested.toSet()))
        return slots.map { if (it.visible) queue.removeFirst() else it.entryId }
    }

    /** [ids] with the item at [from] moved to [to]; indexes out of range leave the list as it is. */
    fun <T> move(
        ids: List<T>,
        from: Int,
        to: Int,
    ): List<T> {
        if (from !in ids.indices || to !in ids.indices || from == to) return ids
        return ids.toMutableList().apply { add(to, removeAt(from)) }
    }
}
