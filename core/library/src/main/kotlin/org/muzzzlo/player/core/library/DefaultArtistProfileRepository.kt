package org.muzzzlo.player.core.library

import androidx.room.withTransaction
import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.database.MuzzzloDatabase
import org.muzzzlo.player.core.database.dao.LibraryDao
import org.muzzzlo.player.core.database.entity.ArtistAliasEntity
import org.muzzzlo.player.core.model.ArtistEdit
import org.muzzzlo.player.core.model.ArtistEditToken
import org.muzzzlo.player.core.model.ArtistProfileRepository
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.NameNormalizer
import org.muzzzlo.player.core.model.RemovedAlias
import javax.inject.Inject

internal class DefaultArtistProfileRepository
    @Inject
    constructor(
        private val database: MuzzzloDatabase,
        private val dao: LibraryDao,
        private val dispatchers: DispatcherProvider,
    ) : ArtistProfileRepository {
        override suspend fun artistName(artistId: Long): String? =
            withContext(dispatchers.io) { dao.artistName(artistId) }

        override suspend fun affectedTrackIds(artistId: Long): List<Long> =
            withContext(dispatchers.io) { dao.trackIdsOfArtist(artistId) }

        override suspend fun begin(
            artistId: Long,
            edit: ArtistEdit,
        ): ArtistEditToken =
            withContext(dispatchers.io) {
                database.withTransaction {
                    val keys = edit.replacement.map(NameNormalizer::key).distinct()
                    val removed = dao.aliasesByKeys(keys).filter { it.artistId == artistId }
                    if (removed.isNotEmpty()) dao.deleteAliasesByKeys(removed.map { it.aliasKey })
                    val snapshot = removed.map { RemovedAlias(it.aliasKey, it.aliasDisplay, it.artistId) }
                    ArtistEditToken(artistId, edit, snapshot)
                }
            }

        override suspend fun commit(token: ArtistEditToken) {
            withContext(dispatchers.io) {
                database.withTransaction {
                    val edit = token.edit
                    val single = edit.replacement.singleOrNull()
                    val oldId = token.artistId
                    val newId = single?.let { currentId(it) }
                    if (newId != null && newId != oldId) {
                        dao.moveAliases(oldId, newId)
                        dao.insertAlias(
                            ArtistAliasEntity(
                                aliasKey = NameNormalizer.key(edit.target),
                                aliasDisplay = edit.target,
                                artistId = newId,
                            ),
                        )
                        movePhoto(oldId, newId)
                    } else if (newId == oldId && single != null) {
                        // Same key, new spelling (a case fix): the artist keeps the id, the name changes.
                        dao.setArtistName(oldId, single)
                    }
                    dao.deleteArtistIfOrphan(oldId)
                }
            }
        }

        override suspend fun rollback(token: ArtistEditToken) {
            withContext(dispatchers.io) {
                database.withTransaction {
                    token.removedAliases.forEach {
                        dao.insertAlias(
                            ArtistAliasEntity(aliasKey = it.key, aliasDisplay = it.display, artistId = it.artistId),
                        )
                    }
                }
            }
        }

        private suspend fun currentId(name: String): Long? {
            val key = NameNormalizer.key(name)
            return dao.aliasArtistId(key) ?: dao.artistIdByKey(key)
        }

        /** The photo is the user's work: it goes to the new artist unless they already have their own. */
        private suspend fun movePhoto(
            from: Long,
            to: Long,
        ) {
            val photo = dao.artistPhotoPath(from) ?: return
            if (dao.artistPhotoPath(to) != null) return
            dao.setArtistPhotoPath(to, photo)
            dao.setArtistPhotoPath(from, null)
        }
    }
