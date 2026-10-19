package org.muzzzlo.player.core.library

import androidx.room.withTransaction
import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.database.MuzzzloDatabase
import org.muzzzlo.player.core.database.dao.LibraryDao
import org.muzzzlo.player.core.model.ArtistCheckRepository
import org.muzzzlo.player.core.model.DispatcherProvider
import javax.inject.Inject

internal class DefaultArtistCheckRepository
    @Inject
    constructor(
        private val database: MuzzzloDatabase,
        private val dao: LibraryDao,
        private val dispatchers: DispatcherProvider,
    ) : ArtistCheckRepository {
        override suspend fun merge(
            keepId: Long,
            variantIds: List<Long>,
        ) {
            withContext(dispatchers.io) {
                // One transaction for the whole group: either every spelling is folded or none.
                database.withTransaction { variantIds.forEach { dao.mergeArtists(it, keepId) } }
            }
        }
    }
