package org.muzzzlo.player.core.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import org.muzzzlo.player.core.database.dao.AlbumRow
import org.muzzzlo.player.core.database.dao.LibraryDao
import org.muzzzlo.player.core.database.dao.ListenEventDao
import org.muzzzlo.player.core.database.dao.TrackArtistName
import org.muzzzlo.player.core.database.dao.TrackGenreName
import org.muzzzlo.player.core.database.dao.TrackPlayStatRow
import org.muzzzlo.player.core.database.entity.TrackEntity
import org.muzzzlo.player.core.model.DispatcherProvider
import org.muzzzlo.player.core.model.TrackFacts
import org.muzzzlo.player.core.model.TrackFactsSource
import javax.inject.Inject

/**
 * [TrackFactsSource] over the present tracks, their artists, albums and genres, and the counted listens (task 2.5.6).
 * Rules are applied in memory over this list: nested "All" / "Any" stays plain Kotlin that table tests cover, and a
 * library of some ten thousand tracks fits easily.
 */
internal class DefaultTrackFactsSource
    @Inject
    constructor(
        private val dao: LibraryDao,
        private val listens: ListenEventDao,
        private val dispatchers: DispatcherProvider,
    ) : TrackFactsSource {
        override fun observeFacts(): Flow<List<TrackFacts>> =
            combine(
                dao.observeTracks(),
                dao.observeTrackArtists(),
                dao.observeAlbums(),
                dao.observeTrackGenres(),
                listens.observeTrackPlayStats(),
            ) { tracks, artists, albums, genres, plays -> trackFactsOf(tracks, artists, albums, genres, plays) }
                .flowOn(dispatchers.default)
    }

private const val MS_PER_SECOND = 1000L

/** Joins the rows into [TrackFacts]; MediaStore gives `date_added` in seconds. */
internal fun trackFactsOf(
    tracks: List<TrackEntity>,
    artists: List<TrackArtistName>,
    albums: List<AlbumRow>,
    genres: List<TrackGenreName>,
    plays: List<TrackPlayStatRow>,
): List<TrackFacts> {
    val artistsByTrack = artists.groupBy({ it.trackId }, { it.name })
    val albumTitles = albums.associate { it.id to it.title }
    val genresByTrack = genres.groupBy({ it.trackId }, { it.name })
    val playsByTrack = plays.associateBy { it.trackId }
    return tracks.filterNot { it.isMissing }.map { entity ->
        val stat = playsByTrack[entity.id]
        TrackFacts(
            track = entity.toTrack(artistsByTrack, albumTitles),
            titleKey = entity.titleKey,
            isFavorite = entity.isFavorite,
            favoritedAtMs = if (entity.isFavorite) entity.favoritedAt ?: 0 else null,
            playCount = stat?.plays ?: 0,
            lastPlayedAtMs = stat?.lastPlayedAt,
            genres = genresByTrack[entity.id].orEmpty(),
            year = entity.year,
            dateAddedMs = entity.dateAdded?.times(MS_PER_SECOND),
        )
    }
}
