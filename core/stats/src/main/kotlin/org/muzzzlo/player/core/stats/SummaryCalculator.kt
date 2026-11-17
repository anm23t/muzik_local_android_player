package org.muzzzlo.player.core.stats

import org.muzzzlo.player.core.model.ArtistImage
import org.muzzzlo.player.core.model.CalendarPeriod
import org.muzzzlo.player.core.model.NameNormalizer
import org.muzzzlo.player.core.model.PeriodSummary
import org.muzzzlo.player.core.model.SummaryAlbum
import org.muzzzlo.player.core.model.SummaryArtist
import org.muzzzlo.player.core.model.SummaryTrack

/**
 * One counted listen of a period as the summary sees it.
 *
 * @property trackId null once the track is gone; then [title] and [snapshotArtists] are all there is.
 * @property title the current title of a present track, else the snapshot.
 * @property snapshotArtists the artist snapshot of the listen (decision 5).
 * @property albumId the album of a present track; null for a deleted track or one without an album.
 */
internal data class SummaryListen(
    val trackId: Long?,
    val title: String,
    val snapshotArtists: List<String>,
    val albumId: Long?,
    val albumTitle: String?,
    val playedMs: Long,
)

/** A library artist linked to a present track. */
internal data class ArtistLink(
    val id: Long,
    val name: String,
    val photoPath: String?,
)

/** Listens and played time of one entry of a top. */
private class Tally {
    var plays = 0
    var playedMs = 0L

    fun add(listen: SummaryListen) {
        plays++
        playedMs += listen.playedMs
    }
}

/**
 * Computes a [PeriodSummary] (task 2.5.11). Pure: the input is the counted listens of the period, the artists of the
 * present tracks ([trackArtists], in tag order) and their genres ([trackGenres]).
 *
 * - Tracks: a present track by id, a deleted one by its snapshot of title and artists.
 * - Albums: listens of present tracks with an album.
 * - Artists: every artist of a track counts once per listen. A present track counts for its library artists; a
 *   deleted one for the names of its snapshot, joined to a library artist with the same [NameNormalizer.key].
 * - Ties go by played time, then by name, then by the order of the first listen.
 * - The favorite genre: the genre with the most listens of present tracks, each genre of a track counting.
 */
internal object SummaryCalculator {
    fun calculate(
        period: CalendarPeriod,
        isFinished: Boolean,
        listens: List<SummaryListen>,
        trackArtists: Map<Long, List<ArtistLink>>,
        trackGenres: Map<Long, List<String>>,
    ): PeriodSummary? {
        if (listens.isEmpty()) return null
        return PeriodSummary(
            period = period,
            isFinished = isFinished,
            plays = listens.size,
            playedMs = listens.sumOf { it.playedMs },
            tracks = topTracks(listens, trackArtists),
            albums = topAlbums(listens),
            artists = topArtists(listens, trackArtists),
            favoriteGenre = favoriteGenre(listens, trackGenres),
        )
    }

    private fun topTracks(
        listens: List<SummaryListen>,
        trackArtists: Map<Long, List<ArtistLink>>,
    ): List<SummaryTrack> {
        val groups = LinkedHashMap<String, Pair<SummaryListen, Tally>>()
        for (listen in listens) {
            val key = listen.trackId?.let { "id:$it" } ?: "gone:${listen.title}\u0000${listen.snapshotArtists}"
            groups.getOrPut(key) { listen to Tally() }.second.add(listen)
        }
        return groups.values
            .ranked { (listen, tally) -> Ranking(tally.plays, tally.playedMs, listen.title) }
            .map { (listen, tally) ->
                val artists = listen.trackId?.let(trackArtists::get)?.map { it.name } ?: listen.snapshotArtists
                SummaryTrack(listen.trackId, listen.title, artists, listen.albumId, tally.plays, tally.playedMs)
            }
    }

    private fun topAlbums(listens: List<SummaryListen>): List<SummaryAlbum> {
        val groups = LinkedHashMap<Long, Pair<String, Tally>>()
        for (listen in listens) {
            val albumId = listen.albumId ?: continue
            groups.getOrPut(albumId) { listen.albumTitle.orEmpty() to Tally() }.second.add(listen)
        }
        return groups.entries
            .ranked { (_, value) -> Ranking(value.second.plays, value.second.playedMs, value.first) }
            .map { (id, value) -> SummaryAlbum(id, value.first, value.second.plays, value.second.playedMs) }
    }

    /** An artist being counted: who it is and which albums its listens came from. */
    private class ArtistTally(
        val link: ArtistLink?,
        val name: String,
    ) {
        val tally = Tally()
        val albums = LinkedHashMap<Long, Tally>()
    }

    private fun topArtists(
        listens: List<SummaryListen>,
        trackArtists: Map<Long, List<ArtistLink>>,
    ): List<SummaryArtist> {
        val known = trackArtists.values.flatten().associateBy { NameNormalizer.key(it.name) }
        val tallies = LinkedHashMap<String, ArtistTally>()
        for (listen in listens) {
            for ((key, link, name) in artistsOf(listen, trackArtists, known)) {
                val artist = tallies.getOrPut(key) { ArtistTally(link, link?.name ?: name) }
                artist.tally.add(listen)
                listen.albumId?.let { artist.albums.getOrPut(it, ::Tally).add(listen) }
            }
        }
        return tallies.values
            .ranked { Ranking(it.tally.plays, it.tally.playedMs, it.name) }
            .map { SummaryArtist(it.link?.id, it.name, it.tally.plays, it.tally.playedMs, imageOf(it)) }
    }

    /** The artists of one listen, each once: (key, library artist or null, name). */
    private fun artistsOf(
        listen: SummaryListen,
        trackArtists: Map<Long, List<ArtistLink>>,
        known: Map<String, ArtistLink>,
    ): List<Triple<String, ArtistLink?, String>> {
        val links = listen.trackId?.let(trackArtists::get).orEmpty()
        val named =
            if (links.isNotEmpty()) {
                links.map { Triple(NameNormalizer.key(it.name), it, it.name) }
            } else {
                listen.snapshotArtists.map { name -> NameNormalizer.key(name).let { Triple(it, known[it], name) } }
            }
        return named.filter { it.first.isNotEmpty() }.distinctBy { it.first }
    }

    /** The photo of the artist, else the cover of the album of the most of its listens in the period. */
    private fun imageOf(artist: ArtistTally): ArtistImage? {
        artist.link?.photoPath?.let { return ArtistImage.Photo(it) }
        val album = artist.albums.entries.maxWithOrNull(compareBy({ it.value.plays }, { it.value.playedMs }))
        return album?.let { ArtistImage.AlbumCover(it.key) }
    }

    private fun favoriteGenre(
        listens: List<SummaryListen>,
        trackGenres: Map<Long, List<String>>,
    ): String? {
        val genres = LinkedHashMap<String, Pair<String, Tally>>()
        for (listen in listens) {
            val names = listen.trackId?.let(trackGenres::get).orEmpty().distinctBy(NameNormalizer::key)
            for (name in names) {
                val key = NameNormalizer.key(name)
                if (key.isNotEmpty()) genres.getOrPut(key) { name to Tally() }.second.add(listen)
            }
        }
        return genres.values.ranked { (name, tally) -> Ranking(tally.plays, tally.playedMs, name) }.firstOrNull()?.first
    }

    /** The order of every top: plays, played time, name; a stable sort keeps the order of first listens after that. */
    private data class Ranking(
        val plays: Int,
        val playedMs: Long,
        val name: String,
    )

    private val rankingOrder =
        compareByDescending<Ranking> { it.plays }
            .thenByDescending { it.playedMs }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }

    private fun <T> Collection<T>.ranked(ranking: (T) -> Ranking): List<T> =
        sortedWith { a, b -> rankingOrder.compare(ranking(a), ranking(b)) }.take(PeriodSummary.TOP)
}
