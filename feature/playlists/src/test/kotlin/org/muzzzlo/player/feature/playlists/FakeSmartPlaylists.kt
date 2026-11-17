package org.muzzzlo.player.feature.playlists

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.muzzzlo.player.core.model.SmartPlaylist
import org.muzzzlo.player.core.model.SmartPlaylistRepository
import org.muzzzlo.player.core.model.SmartRules
import org.muzzzlo.player.core.model.Track
import org.muzzzlo.player.core.model.TrackFacts
import org.muzzzlo.player.core.model.TrackFactsSource

/** Smart playlists in memory; the rules pick from [facts] like the real repository. */
class FakeSmartPlaylists(
    val facts: MutableStateFlow<List<TrackFacts>> = MutableStateFlow(emptyList()),
) : SmartPlaylistRepository,
    TrackFactsSource {
    val playlists = MutableStateFlow<List<SmartPlaylist>>(emptyList())
    val countedRules = mutableListOf<SmartRules>()
    private var nextId = 1L

    override fun observePlaylists(): Flow<List<SmartPlaylist>> = playlists

    override fun observeTracks(rules: SmartRules): Flow<List<Track>> {
        countedRules += rules
        return facts.map { rules.select(it, NOW_MS) }
    }

    override suspend fun playlist(id: Long): SmartPlaylist? = playlists.value.firstOrNull { it.id == id }

    override suspend fun create(
        name: String,
        rules: SmartRules,
    ): Long {
        val id = nextId++
        playlists.update { it + SmartPlaylist(id, name, rules, createdAtMs = 0) }
        return id
    }

    override suspend fun update(
        id: Long,
        name: String,
        rules: SmartRules,
    ) = playlists.update { all -> all.map { if (it.id == id) it.copy(name = name, rules = rules) else it } }

    override suspend fun delete(id: Long) = playlists.update { all -> all.filterNot { it.id == id } }

    override fun observeFacts(): Flow<List<TrackFacts>> = facts

    private companion object {
        const val NOW_MS = 0L
    }
}
