package org.muzzzlo.player.core.model

import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/**
 * A counted listen as the daily mix sees it (task 2.5.8).
 *
 * @property trackId null once the track is gone; its artists still count through the snapshot.
 * @property artists the artist snapshot of the listen (decision 5).
 * @property epochDay the local day of the listen.
 * @property fromGenerated the listen came from a generated playlist (`listen_event.source_kind`).
 */
data class MixListen(
    val trackId: Long?,
    val artists: List<String>,
    val startedAtMs: Long,
    val epochDay: Long,
    val fromGenerated: Boolean = false,
)

/**
 * The daily mix (task 2.5.8): one playlist a day of [SIZE] tracks, made offline from the library.
 *
 * - Seed: the artists and genres of the listens of the last [SEED_DAYS] days; a listen weighs less every day
 *   (half after [HALF_LIFE_DAYS] days), a listen from a generated playlist weighs [GENERATED_WEIGHT] of a normal one,
 *   so the mix does not feed on itself. Without listens in those days the whole history is the seed; without any
 *   listen there is no mix.
 * - Filling: about [SEED_SHARE] tracks of the seed artists and genres, [NEIGHBOUR_SHARE] of "neighbours" (artists
 *   heard on the same days as the seed artists) and [FORGOTTEN_SHARE] forgotten favorites; tracks not played for
 *   long weigh more. A short pool is made up from the others.
 * - Variety: at most [MAX_PER_ARTIST] tracks per artist (each artist of a track counts), no track played in the last
 *   [RECENT_DAYS] days, and tracks of yesterday's mix only when nothing else is left.
 *
 * The randomness comes from the day, so the mix does not change during the day. The numbers are assistant's
 * decisions except the weight of generated listens (owner's decision on the principle).
 */
object DailyMix {
    const val SIZE = GeneratedSets.SIZE
    const val SEED_DAYS = 14
    const val HALF_LIFE_DAYS = 7.0
    const val GENERATED_WEIGHT = 0.1
    const val MAX_PER_ARTIST = 3
    const val RECENT_DAYS = 2
    const val SEED_SHARE = 12
    const val NEIGHBOUR_SHARE = 5
    const val FORGOTTEN_SHARE = 3

    private const val MS_PER_DAY = 24.0 * 60 * 60 * 1000
    private const val GENRE_FACTOR = 0.5
    private const val STALE_CAP_DAYS = 60.0
    private const val STALE_SCALE_DAYS = 30.0

    /** Track ids of the mix in play order; empty without any listen. */
    fun build(
        facts: List<TrackFacts>,
        listens: List<MixListen>,
        yesterdayMix: Set<Long>,
        nowMs: Long,
        random: Random,
    ): List<Long> {
        if (listens.isEmpty()) return emptyList()
        val byId = facts.associateBy { it.track.id }
        val seed = Seed.of(listens, byId, nowMs)
        val recentlyPlayed = recentTracks(listens, facts, nowMs)
        val candidates = facts.filter { it.track.id !in recentlyPlayed }
        val pools =
            listOf(
                Pool(SEED_SHARE, candidates.weighted(nowMs) { seed.score(it) }),
                Pool(NEIGHBOUR_SHARE, candidates.weighted(nowMs) { seed.neighbourScore(it) }),
                Pool(FORGOTTEN_SHARE, candidates.filter { it.isForgottenFavorite(nowMs) }.map { it to 1.0 }),
            ).map { it.ordered(random) }
        val picked = Picker(yesterdayMix).pick(pools)
        return picked.shuffled(random)
    }

    /** Seed artists and genres with their scores, and the neighbours of the seed artists. */
    private class Seed(
        val artists: Map<String, Double>,
        val genres: Map<String, Double>,
        val neighbours: Map<String, Double>,
    ) {
        fun score(fact: TrackFacts): Double =
            fact.track.artists.sumOf { artists[NameNormalizer.key(it)] ?: 0.0 } +
                GENRE_FACTOR * fact.genres.sumOf { genres[NameNormalizer.key(it)] ?: 0.0 }

        fun neighbourScore(fact: TrackFacts): Double =
            fact.track.artists.sumOf { neighbours[NameNormalizer.key(it)] ?: 0.0 }

        companion object {
            fun of(
                listens: List<MixListen>,
                facts: Map<Long, TrackFacts>,
                nowMs: Long,
            ): Seed {
                val window = listens.filter { nowMs - it.startedAtMs <= SEED_DAYS * MS_PER_DAY }.ifEmpty { listens }
                val artists = HashMap<String, Double>()
                val genres = HashMap<String, Double>()
                for (listen in window) {
                    val weight = weightOf(listen, nowMs)
                    listen.artists.forEach { artists.merge(NameNormalizer.key(it), weight, Double::plus) }
                    facts[listen.trackId]?.genres?.forEach {
                        genres.merge(
                            NameNormalizer.key(it),
                            weight,
                            Double::plus,
                        )
                    }
                }
                return Seed(artists, genres, neighboursOf(listens, artists))
            }

            /**
             * Artists heard on the days the seed artists were heard, scored by how much seed those days hold; the seed
             * artists themselves are not neighbours.
             */
            private fun neighboursOf(
                listens: List<MixListen>,
                seed: Map<String, Double>,
            ): Map<String, Double> {
                val neighbours = HashMap<String, Double>()
                listens.groupBy { it.epochDay }.values.forEach { day ->
                    val heard = day.flatMap { it.artists }.map(NameNormalizer::key).toSet()
                    val seedOfDay = heard.sumOf { seed[it] ?: 0.0 }
                    if (seedOfDay > 0) {
                        heard.filter { it !in seed }.forEach {
                            neighbours.merge(
                                it,
                                seedOfDay,
                                Double::plus,
                            )
                        }
                    }
                }
                return neighbours
            }
        }
    }

    /** The weight of one listen: fresh ones more, ones from generated playlists much less. */
    internal fun weightOf(
        listen: MixListen,
        nowMs: Long,
    ): Double {
        val ageDays = ((nowMs - listen.startedAtMs) / MS_PER_DAY).coerceAtLeast(0.0)
        val fresh = 0.5.pow(ageDays / HALF_LIFE_DAYS)
        return if (listen.fromGenerated) fresh * GENERATED_WEIGHT else fresh
    }

    private fun recentTracks(
        listens: List<MixListen>,
        facts: List<TrackFacts>,
        nowMs: Long,
    ): Set<Long> {
        val cutoff = nowMs - RECENT_DAYS * MS_PER_DAY
        return listens.filter { it.startedAtMs >= cutoff }.mapNotNullTo(HashSet()) { it.trackId } +
            facts.filter { (it.lastPlayedAtMs ?: Long.MIN_VALUE) >= cutoff }.map { it.track.id }
    }

    /** Tracks with a positive [score], times how long ago they were played. */
    private fun List<TrackFacts>.weighted(
        nowMs: Long,
        score: (TrackFacts) -> Double,
    ): List<Pair<TrackFacts, Double>> =
        mapNotNull { fact -> score(fact).takeIf { it > 0 }?.let { fact to it * staleness(fact, nowMs) } }

    /** 1 for a track played just now up to 3 for one not played for two months or never. */
    private fun staleness(
        fact: TrackFacts,
        nowMs: Long,
    ): Double {
        val days = fact.lastPlayedAtMs?.let { (nowMs - it) / MS_PER_DAY } ?: STALE_CAP_DAYS
        return 1 + days.coerceIn(0.0, STALE_CAP_DAYS) / STALE_SCALE_DAYS
    }

    private fun TrackFacts.isForgottenFavorite(nowMs: Long): Boolean {
        val loved = isFavorite || (track.ratingTenths ?: 0) >= GeneratedSets.HIGH_RATING
        val last = lastPlayedAtMs ?: return loved
        return loved && nowMs - last > GeneratedSets.FORGOTTEN_DAYS * MS_PER_DAY
    }

    /** A pool of candidates and the share of the mix it fills. */
    private class Pool(
        val share: Int,
        val candidates: List<Pair<TrackFacts, Double>>,
    ) {
        /**
         * The candidates in a weighted random order (Efraimidis and Spirakis: the key `ln(u) / w`, largest first),
         * so heavier tracks tend to come first without the light ones being ruled out.
         */
        fun ordered(random: Random): OrderedPool =
            OrderedPool(
                share,
                candidates
                    .map { (fact, weight) -> fact to ln(random.nextDouble().coerceAtLeast(Double.MIN_VALUE)) / weight }
                    .sortedByDescending { it.second }
                    .map { it.first },
            )
    }

    private class OrderedPool(
        val share: Int,
        val tracks: List<TrackFacts>,
    )

    /** Takes tracks pool by pool, keeping the limit per artist and leaving yesterday's mix to the end. */
    private class Picker(
        private val yesterday: Set<Long>,
    ) {
        private val picked = LinkedHashSet<Long>()
        private val perArtist = HashMap<String, Int>()

        fun pick(pools: List<OrderedPool>): List<Long> {
            // Each pool its share first, then whatever is left, then yesterday's tracks as a last resort.
            pools.forEach { take(it.tracks, it.share, allowYesterday = false) }
            pools.forEach { take(it.tracks, SIZE, allowYesterday = false) }
            pools.forEach { take(it.tracks, SIZE, allowYesterday = true) }
            return picked.toList()
        }

        private fun take(
            tracks: List<TrackFacts>,
            share: Int,
            allowYesterday: Boolean,
        ) {
            var taken = 0
            for (fact in tracks) {
                if (taken >= share || picked.size >= SIZE) return
                if (fits(fact, allowYesterday)) {
                    add(fact)
                    taken++
                }
            }
        }

        private fun fits(
            fact: TrackFacts,
            allowYesterday: Boolean,
        ): Boolean {
            val id = fact.track.id
            if (id in picked || (!allowYesterday && id in yesterday)) return false
            return fact.artistKeys().all { (perArtist[it] ?: 0) < MAX_PER_ARTIST }
        }

        private fun add(fact: TrackFacts) {
            picked += fact.track.id
            fact.artistKeys().forEach { perArtist.merge(it, 1, Int::plus) }
        }

        private fun TrackFacts.artistKeys() = track.artists.map(NameNormalizer::key).toSet()
    }
}
