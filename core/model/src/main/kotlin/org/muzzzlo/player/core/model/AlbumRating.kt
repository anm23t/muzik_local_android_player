package org.muzzzlo.player.core.model

/**
 * The rating of an album (task 2.0.3): the arithmetic mean of the ratings of its tracks. It is not stored, it is
 * counted from the track ratings in tenths (CLAUDE.md, decision 12), so nothing can get out of step with them.
 */
object AlbumRating {
    /**
     * Mean of [ratingsTenths] rounded to a tenth, half up; null when no track is rated. Tracks without a rating
     * (null) are left out, a rating of 0.0 counts. Tracks of missing files must not be passed.
     */
    fun of(ratingsTenths: List<Int?>): Int? {
        val rated = ratingsTenths.filterNotNull()
        return of(rated.sumOf { it.toLong() }, rated.size)
    }

    /** The same rule for a sum of [ratedCount] ratings in tenths, as the database sums them. */
    fun of(
        sumTenths: Long,
        ratedCount: Int,
    ): Int? {
        if (ratedCount == 0) return null
        // Half up in integers: floor(sum / count + 1/2).
        return ((2 * sumTenths + ratedCount) / (2L * ratedCount)).toInt()
    }
}
