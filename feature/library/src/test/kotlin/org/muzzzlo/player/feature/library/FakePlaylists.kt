package org.muzzzlo.player.feature.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.muzzzlo.player.core.model.Playlist
import org.muzzzlo.player.core.model.PlaylistEntry
import org.muzzzlo.player.core.model.PlaylistRepository

/** In-memory playlists: names and track ids in order; entry ids are not tracked. */
class FakePlaylists : PlaylistRepository {
    val playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val tracks = mutableMapOf<Long, MutableList<Long>>()

    override fun observePlaylists(): Flow<List<Playlist>> = playlists

    override fun observePlaylist(playlistId: Long): Flow<Playlist?> =
        playlists.map { list -> list.firstOrNull { it.id == playlistId } }

    override fun observeEntries(playlistId: Long): Flow<List<PlaylistEntry>> = error("Not used")

    override suspend fun create(name: String): Long {
        val id = (playlists.value.maxOfOrNull { it.id } ?: 0) + 1
        playlists.value += Playlist(id, name, 0, null, 0, null)
        tracks[id] = mutableListOf()
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
        tracks.remove(playlistId)
    }

    override suspend fun addTracks(
        playlistId: Long,
        trackIds: List<Long>,
    ) {
        tracks.getOrPut(playlistId) { mutableListOf() } += trackIds
    }

    override suspend fun removeEntry(entryId: Long) = error("Not used")

    override suspend fun reorder(
        playlistId: Long,
        entryIds: List<Long>,
    ) = error("Not used")

    override suspend fun markPlayed(playlistId: Long) = Unit
}
