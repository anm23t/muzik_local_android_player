package org.muzzzlo.player.core.library

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.database.MuzzzloDatabase
import org.muzzzlo.player.core.database.dao.LibraryDao
import org.muzzzlo.player.core.database.entity.ArtistCompoundEntity
import org.muzzzlo.player.core.database.entity.TrackArtistEntity
import org.muzzzlo.player.core.model.CompoundNames
import org.muzzzlo.player.core.model.CompoundRepository
import org.muzzzlo.player.core.model.CompoundRule
import org.muzzzlo.player.core.model.CompoundRules
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.NameNormalizer
import javax.inject.Inject

internal class DefaultCompoundRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MuzzzloDatabase,
        private val dao: LibraryDao,
        private val dispatchers: DispatcherProvider,
    ) : CompoundRepository {
        override fun observeRules(): Flow<List<CompoundRule>> =
            dao.observeCompoundRules().map { rows ->
                rows.map { CompoundRule(it.id, it.displayName, it.builtinId, it.enabled) }
            }

        override suspend fun compoundNames(): CompoundNames =
            withContext(dispatchers.io) { CompoundRules.compoundNames(dao.enabledCompoundKeys()) }

        override suspend fun syncBuiltIn() {
            withContext(dispatchers.io) {
                val entries = JsonLite.builtInEntries(readBuiltInList())
                database.withTransaction {
                    val known = dao.knownBuiltinIds().toSet()
                    entries.filter { it.first !in known }.forEach { (id, name) ->
                        dao.insertCompoundRule(newRule(name, id))
                    }
                }
            }
        }

        override suspend fun remember(name: String): Int =
            withContext(dispatchers.io) {
                val key = CompoundRules.matchKey(name)
                if (CompoundRules.fragmentsOfKey(key).size < 2) return@withContext -1
                changeRules { dao.insertCompoundRule(newRule(name.trim(), builtinId = null)) > 0 }
            }

        override suspend fun remove(id: Long): Int =
            withContext(dispatchers.io) {
                changeRules {
                    val rule = dao.compoundRules().firstOrNull { it.id == id } ?: return@changeRules false
                    // A built-in rule is switched off, not deleted: an update must not bring it back.
                    if (rule.builtinId == null) dao.deleteCompoundRule(id) else dao.setCompoundRuleEnabled(id, false)
                    true
                }.coerceAtLeast(0)
            }

        override suspend fun enable(id: Long): Int =
            withContext(dispatchers.io) {
                changeRules {
                    dao.setCompoundRuleEnabled(id, true)
                    true
                }.coerceAtLeast(0)
            }

        /**
         * Runs [change] on the table and, when it did something, cuts the affected tracks again in the same
         * transaction. Returns the number of changed tracks, -1 when [change] did nothing.
         */
        private suspend fun changeRules(change: suspend () -> Boolean): Int =
            database.withTransaction {
                val before = CompoundRules.compoundNames(dao.enabledCompoundKeys())
                if (!change()) return@withTransaction -1
                val after = CompoundRules.compoundNames(dao.enabledCompoundKeys())
                recut(before, after)
            }

        private suspend fun recut(
            before: CompoundNames,
            after: CompoundNames,
        ): Int {
            val raw = dao.trackArtistTagValues().associate { it.id to JsonLite.strings(it.artistTagValues) }
            val changed = CompoundRecalculation.changedTracks(raw, before, after)
            changed.forEach { track ->
                val artistIds = track.artists.map { dao.artistId(it, NameNormalizer.key(it)) }
                dao.clearTrackArtists(track.trackId)
                dao.insertTrackArtists(artistIds.mapIndexed { i, id -> TrackArtistEntity(track.trackId, id, i) })
            }
            if (changed.isNotEmpty()) {
                // Album artists are not stored raw: the next scan reads these files again and fixes them.
                dao.forgetDateModified(changed.map { it.trackId })
                dao.deleteOrphanArtists()
            }
            return changed.size
        }

        private fun newRule(
            name: String,
            builtinId: String?,
        ) = ArtistCompoundEntity(
            displayName = name,
            matchKey = CompoundRules.matchKey(name),
            builtinId = builtinId,
            createdAt = System.currentTimeMillis() / MILLIS_IN_SECOND,
        )

        private fun readBuiltInList(): String =
            context.assets.open(BUILT_IN_ASSET).use { it.readBytes().toString(Charsets.UTF_8) }

        private companion object {
            const val BUILT_IN_ASSET = "compound_artists.json"
            const val MILLIS_IN_SECOND = 1000L
        }
    }
