package org.muzzzlo.player.core.library

import kotlinx.coroutines.withContext
import org.muzzzlo.player.core.database.dao.LibraryDao
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.TrackMarks
import org.muzzzlo.player.core.model.TrackMarksRepository
import java.time.Clock
import javax.inject.Inject

/** [TrackMarksRepository] over the `rating_tenths`, `is_favorite` and `favorited_at` columns of `track`. */
internal class DefaultTrackMarksRepository
    @Inject
    constructor(
        private val dao: LibraryDao,
        private val dispatchers: DispatcherProvider,
        private val clock: Clock,
    ) : TrackMarksRepository {
        override suspend fun marks(trackId: Long): TrackMarks =
            withContext(dispatchers.io) {
                dao.marksOf(trackId)?.let { TrackMarks(it.isFavorite, it.ratingTenths) } ?: TrackMarks()
            }

        override suspend fun setFavorite(
            trackId: Long,
            favorite: Boolean,
        ) = withContext(dispatchers.io) {
            if (favorite && dao.isFavorite(trackId) == true) return@withContext
            dao.setFavorite(trackId, favorite, if (favorite) clock.millis() else null)
        }

        override suspend fun setRating(
            trackId: Long,
            ratingTenths: Int?,
        ) = withContext(dispatchers.io) {
            dao.setRating(trackId, ratingTenths?.coerceIn(0, TrackMarks.MAX_RATING_TENTHS))
        }
    }
