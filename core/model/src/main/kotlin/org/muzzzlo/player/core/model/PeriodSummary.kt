package org.muzzzlo.player.core.model

import kotlinx.coroutines.flow.Flow

/**
 * A track of a period summary (task 2.5.11). [trackId] is null for a track that is gone; it is known by the snapshot
 * of its title and artists (decision 5).
 */
data class SummaryTrack(
    val trackId: Long?,
    val title: String,
    val artists: List<String>,
    val albumId: Long?,
    val plays: Int,
    val playedMs: Long,
)

/** An album of a summary: the counted listens of its tracks that are still in the library. */
data class SummaryAlbum(
    val albumId: Long,
    val title: String,
    val plays: Int,
    val playedMs: Long,
)

/** The picture of an artist in a summary: the photo the user added (decision 10) or a cover of their album. */
sealed interface ArtistImage {
    data class Photo(val path: String) : ArtistImage

    /** The album of the artist listened to most in the period. */
    data class AlbumCover(val albumId: Long) : ArtistImage
}

/**
 * An artist of a summary; every artist of a track gets the listen. [artistId] is null for an artist known only from
 * snapshots of deleted tracks; [image] is null when there is neither a photo nor an album.
 */
data class SummaryArtist(
    val artistId: Long?,
    val name: String,
    val plays: Int,
    val playedMs: Long,
    val image: ArtistImage?,
)

/**
 * What was listened to in one calendar period (task 2.5.11): up to [PeriodSummary.TOP] tracks, albums and artists,
 * the time played and the favorite genre. Computed from `listen_event` whenever it is asked for, so every past
 * period is there.
 *
 * @property isFinished false for the period that is still running ("so far").
 * @property favoriteGenre the genre with the most listens; null when no listened track has a genre.
 */
data class PeriodSummary(
    val period: CalendarPeriod,
    val isFinished: Boolean,
    val plays: Int,
    val playedMs: Long,
    val tracks: List<SummaryTrack>,
    val albums: List<SummaryAlbum>,
    val artists: List<SummaryArtist>,
    val favoriteGenre: String?,
) {
    val topTrack: SummaryTrack? get() = tracks.firstOrNull()
    val topAlbum: SummaryAlbum? get() = albums.firstOrNull()
    val topArtist: SummaryArtist? get() = artists.firstOrNull()

    companion object {
        /** Places in each top. */
        const val TOP = 5
    }
}

/** Period summaries over the listen history. Implemented in `core:stats`. */
interface SummaryRepository {
    /** The summary of [period]; null when nothing was listened to in it. */
    suspend fun summary(period: CalendarPeriod): PeriodSummary?

    /** Whether [period] has any counted listen; follows new listens. */
    fun hasListens(period: CalendarPeriod): Flow<Boolean>
}
