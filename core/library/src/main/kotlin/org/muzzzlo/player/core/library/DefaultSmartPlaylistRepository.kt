package org.muzzzlo.player.core.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.database.dao.SmartPlaylistDao
import org.muzzzlo.player.core.database.entity.SmartPlaylistEntity
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.SmartPlaylist
import org.muzzzlo.player.core.model.SmartPlaylistRepository
import org.muzzzlo.player.core.model.SmartRules
import org.muzzzlo.player.core.model.SmartRulesJson
import org.muzzzlo.player.core.model.Track
import org.muzzzlo.player.core.model.TrackFactsSource
import java.time.Clock
import javax.inject.Inject

/** [SmartPlaylistRepository] over `smart_playlist` and [TrackFactsSource] (task 2.5.6). */
internal class DefaultSmartPlaylistRepository
    @Inject
    constructor(
        private val dao: SmartPlaylistDao,
        private val facts: TrackFactsSource,
        private val clock: Clock,
        private val dispatchers: DispatcherProvider,
    ) : SmartPlaylistRepository {
        override fun observePlaylists(): Flow<List<SmartPlaylist>> =
            dao.observeAll().map { rows -> rows.map { it.toModel() } }.flowOn(dispatchers.default)

        override fun observeTracks(rules: SmartRules): Flow<List<Track>> =
            facts.observeFacts().map { rules.select(it, clock.millis()) }.flowOn(dispatchers.default)

        override suspend fun playlist(id: Long): SmartPlaylist? =
            withContext(dispatchers.io) { dao.byId(id)?.toModel() }

        override suspend fun create(
            name: String,
            rules: SmartRules,
        ): Long =
            withContext(dispatchers.io) {
                dao.insert(
                    SmartPlaylistEntity(
                        name = name,
                        rulesJson = SmartRulesJson.encode(rules),
                        createdAt = clock.millis(),
                    ),
                )
            }

        override suspend fun update(
            id: Long,
            name: String,
            rules: SmartRules,
        ) = withContext(dispatchers.io) { dao.update(id, name, SmartRulesJson.encode(rules)) }

        override suspend fun delete(id: Long) = withContext(dispatchers.io) { dao.delete(id) }
    }

/** Broken JSON reads as rules that match nothing, so a damaged row does not show the whole library. */
internal fun SmartPlaylistEntity.toModel() =
    SmartPlaylist(
        id = id,
        name = name,
        rules = SmartRulesJson.decode(rulesJson) ?: SmartRules.NOTHING,
        createdAtMs = createdAt,
    )
