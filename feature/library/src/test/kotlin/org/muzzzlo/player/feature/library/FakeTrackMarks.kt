package org.muzzzlo.player.feature.library

import org.muzzzlo.player.core.model.TrackMarks
import org.muzzzlo.player.core.model.TrackMarksRepository

/** In-memory favorites and ratings; [favoriteCalls] lists the `setFavorite` calls in order. */
class FakeTrackMarks : TrackMarksRepository {
    val marks = mutableMapOf<Long, TrackMarks>()
    val favoriteCalls = mutableListOf<Pair<Long, Boolean>>()

    override suspend fun marks(trackId: Long): TrackMarks = marks[trackId] ?: TrackMarks()

    override suspend fun setFavorite(
        trackId: Long,
        favorite: Boolean,
    ) {
        favoriteCalls += trackId to favorite
        marks[trackId] = marks(trackId).copy(isFavorite = favorite)
    }

    override suspend fun setRating(
        trackId: Long,
        ratingTenths: Int?,
    ) {
        marks[trackId] = marks(trackId).copy(ratingTenths = ratingTenths)
    }
}
