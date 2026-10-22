package org.muzzzlo.player.core.model

/**
 * The favorite flag and the rating of a track. They live only in the database and the backup
 * (CLAUDE.md, decisions 4 and 12).
 *
 * @property ratingTenths 0..100 (67 means 6.7); null when the track is not rated.
 */
data class TrackMarks(
    val isFavorite: Boolean = false,
    val ratingTenths: Int? = null,
) {
    companion object {
        const val MAX_RATING_TENTHS = 100
    }
}

/** Reads and changes the favorite flag and the rating of one track. */
interface TrackMarksRepository {
    /** Current marks of the track; [TrackMarks] without marks for an unknown track. */
    suspend fun marks(trackId: Long): TrackMarks

    /** Sets or clears the favorite flag. A track that is a favorite already keeps its place in "Favorites". */
    suspend fun setFavorite(
        trackId: Long,
        favorite: Boolean,
    )

    /** Sets the rating (clamped to 0..100 tenths) or clears it with null. */
    suspend fun setRating(
        trackId: Long,
        ratingTenths: Int?,
    )
}
