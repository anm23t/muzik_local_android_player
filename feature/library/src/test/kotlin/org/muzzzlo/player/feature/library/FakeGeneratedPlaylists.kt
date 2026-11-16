package org.muzzzlo.player.feature.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.muzzzlo.player.core.model.GeneratedKind
import org.muzzzlo.player.core.model.GeneratedPlaylist
import org.muzzzlo.player.core.model.GeneratedPlaylistRepository
import org.muzzzlo.player.core.model.SavedSnapshot
import org.muzzzlo.player.core.model.Track

/** Generated playlists kept in memory; [ensured] counts the calls of [ensureToday]. */
class FakeGeneratedPlaylists : GeneratedPlaylistRepository {
    val playlists = MutableStateFlow<List<GeneratedPlaylist>>(emptyList())
    val tracks = MutableStateFlow<Map<GeneratedKind, List<Track>>>(emptyMap())
    val snapshots = mutableListOf<Pair<GeneratedKind, String>>()
    var ensured = 0

    override suspend fun ensureToday() {
        ensured++
    }

    override fun observePlaylists(): Flow<List<GeneratedPlaylist>> =
        playlists.map { list -> list.filter { it.isShown } }

    override fun observePlaylist(kind: GeneratedKind): Flow<GeneratedPlaylist?> =
        playlists.map { list -> list.firstOrNull { it.kind == kind } }

    override fun observeTracks(kind: GeneratedKind): Flow<List<Track>> = tracks.map { it[kind].orEmpty() }

    override suspend fun saveSnapshot(
        kind: GeneratedKind,
        name: String,
    ): SavedSnapshot {
        snapshots += kind to name
        return SavedSnapshot(playlistId = snapshots.size.toLong(), name = "$name 2")
    }
}
