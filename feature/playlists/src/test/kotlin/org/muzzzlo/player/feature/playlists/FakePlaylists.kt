package org.muzzzlo.player.feature.playlists

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.muzzzlo.player.core.model.Playlist
import org.muzzzlo.player.core.model.PlaylistEntry
import org.muzzzlo.player.core.model.PlaylistRepository
import org.muzzzlo.player.core.model.Track

/** In-memory playlists with entries; [tracks] resolves track ids to tracks. Records the calls tests check. */
class FakePlaylists(
    private val tracks: Map<Long, Track> = emptyMap(),
) : PlaylistRepository {
    val playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val entries = MutableStateFlow<Map<Long, List<PlaylistEntry>>>(emptyMap())
    val played = mutableListOf<Long>()
    val reordered = mutableListOf<List<Long>>()
    private var nextEntryId = 100L

    override fun observePlaylists(): Flow<List<Playlist>> = playlists

    override fun observePlaylist(playlistId: Long): Flow<Playlist?> =
        playlists.map { list -> list.firstOrNull { it.id == playlistId } }

    override fun observeEntries(playlistId: Long): Flow<List<PlaylistEntry>> = entries.map { it[playlistId].orEmpty() }

    override suspend fun create(name: String): Long {
        val id = (playlists.value.maxOfOrNull { it.id } ?: 0) + 1
        playlists.value += Playlist(id, name, 0, null, 0, null)
        return id
    }

    override suspend fun rename(
        playlistId: Long,
        name: String,
    ) {
        playlists.value = playlists.value.map { if (it.id == playlistId) it.copy(name = name) else it }
    }

    override suspend fun delete(playlistId: Long) {
        playlists.value = playlists.value.filter { it.id != playlistId }
        entries.value -= playlistId
    }

    override suspend fun addTracks(
        playlistId: Long,
        trackIds: List<Long>,
    ) {
        val added = trackIds.map { PlaylistEntry(nextEntryId++, tracks.getValue(it)) }
        entries.value += playlistId to (entries.value[playlistId].orEmpty() + added)
    }

    override suspend fun removeEntry(entryId: Long) {
        entries.value = entries.value.mapValues { (_, list) -> list.filter { it.entryId != entryId } }
    }

    override suspend fun reorder(
        playlistId: Long,
        entryIds: List<Long>,
    ) {
        reordered += entryIds
        val byId = entries.value[playlistId].orEmpty().associateBy { it.entryId }
        entries.value += playlistId to entryIds.mapNotNull(byId::get)
    }

    override suspend fun markPlayed(playlistId: Long) {
        played += playlistId
    }
}
