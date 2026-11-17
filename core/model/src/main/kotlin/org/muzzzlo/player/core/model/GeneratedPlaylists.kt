package org.muzzzlo.player.core.model

import kotlinx.coroutines.flow.Flow
import kotlin.random.Random

/**
 * A kind of playlist the app makes itself (task 2.5.7). Such playlists are shown only on the home screen and are
 * rebuilt once a day; "Add" keeps today's set as a playlist of the user.
 *
 * @property key stored in `generated_playlist.kind` and `listen_event.source_kind`; never changes.
 */
enum class GeneratedKind(val key: String) {
    /** One mix a day from the listens of the last two weeks ([DailyMix], task 2.5.8); first on the home screen. */
    DAILY_MIX("dailyMix"),

    /** The most played tracks of the season that ends or just ended ([SeasonalWindow], task 2.5.9). */
    MOST_PLAYED_SEASON("mostPlayedSeason"),

    /** The most played tracks of the year that ends or just ended (task 2.5.9). */
    MOST_PLAYED_YEAR("mostPlayedYear"),

    /** The most played tracks of the last 30 days. */
    ON_REPEAT("onRepeat"),

    /** Favorites and tracks rated 8.0 and up that were not played for 60 days or never, picked at random. */
    FORGOTTEN_FAVORITES("forgottenFavorites"),

    /** Random tracks rated 8.0 and up; another pick every day, unlike the full "Top songs". */
    BEST_RATED("bestRated"),

    /** The latest added tracks. */
    RECENTLY_ADDED("recentlyAdded"),
    ;

    /** Built for a calendar period and shown only in its window ([SeasonalWindow]), not every day. */
    val isSeasonal: Boolean get() = this == MOST_PLAYED_SEASON || this == MOST_PLAYED_YEAR

    companion object {
        /** The kinds built anew every day. */
        val daily: List<GeneratedKind> get() = entries.filterNot { it.isSeasonal }

        fun fromKey(key: String?): GeneratedKind? = entries.firstOrNull { it.key == key }
    }
}

/**
 * What the sets are made of: the facts of the present tracks ([TrackFacts]), the counted listens of each track in
 * the last [GeneratedSets.ON_REPEAT_DAYS] days, for the daily mix every counted listen and yesterday's mix, and for a
 * seasonal set the counted listens of each track in its period ([periodPlays]).
 */
data class GeneratedInput(
    val facts: List<TrackFacts>,
    val recentPlays: Map<Long, Int>,
    val nowMs: Long,
    val epochDay: Long,
    val listens: List<MixListen> = emptyList(),
    val yesterdayMix: Set<Long> = emptySet(),
    val periodPlays: Map<Long, Int> = emptyMap(),
)

/** The rules of the ready-made sets (task 2.5.7). Pure functions: the randomness comes from the day. */
object GeneratedSets {
    /** Tracks in a set (assistant's decision). */
    const val SIZE = 20

    /** A set with fewer tracks is not shown; a seasonal set shows with any track (owner's decision). */
    const val MIN_SHOWN = 5

    /** Tracks in a seasonal set (assistant's decision). */
    const val SEASONAL_SIZE = 50

    const val ON_REPEAT_DAYS = 30
    const val FORGOTTEN_DAYS = 60

    /** Rating from which a track counts as loved, in tenths (8.0). */
    const val HIGH_RATING = 80

    private const val MS_PER_DAY = 24L * 60 * 60 * 1000
    private const val SEED_DAY_FACTOR = 1_000_003L

    /** Track ids of the set of [kind] for the day of [input], in play order. */
    fun build(
        kind: GeneratedKind,
        input: GeneratedInput,
    ): List<Long> {
        val random = random(kind, input.epochDay)
        val tracks =
            when (kind) {
                GeneratedKind.MOST_PLAYED_SEASON, GeneratedKind.MOST_PLAYED_YEAR ->
                    return mostPlayed(input.facts, input.periodPlays).take(SEASONAL_SIZE).map { it.track.id }
                GeneratedKind.ON_REPEAT -> mostPlayed(input.facts, input.recentPlays)
                GeneratedKind.FORGOTTEN_FAVORITES ->
                    input.facts.filter { it.isLoved() && it.isForgotten(input.nowMs) }.shuffled(
                        random,
                    )
                GeneratedKind.BEST_RATED -> input.facts.filter { it.isHighlyRated() }.shuffled(random)
                GeneratedKind.RECENTLY_ADDED -> recentlyAdded(input)
                GeneratedKind.DAILY_MIX ->
                    return DailyMix.build(input.facts, input.listens, input.yesterdayMix, input.nowMs, random)
            }
        return tracks.take(SIZE).map { it.track.id }
    }

    /** The randomness of a set: the same for one kind on one day, different on the next day. */
    fun random(
        kind: GeneratedKind,
        epochDay: Long,
    ): Random = Random(epochDay * SEED_DAY_FACTOR + kind.key.hashCode())

    /** Tracks with listens in [plays], the most played first; ties by the latest listen, the title, the id. */
    private fun mostPlayed(
        facts: List<TrackFacts>,
        plays: Map<Long, Int>,
    ): List<TrackFacts> =
        facts
            .filter { (plays[it.track.id] ?: 0) > 0 }
            .sortedWith(
                compareByDescending<TrackFacts> { plays[it.track.id] ?: 0 }
                    .thenByDescending { it.lastPlayedAtMs ?: 0 }
                    .thenBy { it.titleKey }
                    .thenBy { it.track.id },
            )

    private fun recentlyAdded(input: GeneratedInput): List<TrackFacts> =
        input.facts
            .filter { it.dateAddedMs != null }
            .sortedWith(
                compareByDescending<TrackFacts> { it.dateAddedMs }.thenBy { it.titleKey }.thenBy { it.track.id },
            )

    private fun TrackFacts.isHighlyRated() = (track.ratingTenths ?: 0) >= HIGH_RATING

    private fun TrackFacts.isLoved() = isFavorite || isHighlyRated()

    private fun TrackFacts.isForgotten(nowMs: Long): Boolean {
        val last = lastPlayedAtMs ?: return true
        return last < nowMs - FORGOTTEN_DAYS * MS_PER_DAY
    }
}

/**
 * Gradient covers of generated and smart playlists (task 2.5.7, `DESIGN.md`): an index into the pairs of colors of
 * `core:designsystem` (`CoverGradients`), which has exactly [COUNT] of them. The dynamic accent does not touch them.
 */
object CoverGradients {
    const val COUNT = 12

    private const val ON_REPEAT = 0
    private const val FORGOTTEN_FAVORITES = 1
    private const val BEST_RATED = 2
    private const val RECENTLY_ADDED = 3
    private const val WINTER = 4
    private const val SPRING = 5
    private const val SUMMER = 6
    private const val AUTUMN = 7
    private const val YEAR = 8

    /** The pair of a set: fixed per ready-made kind, by [epochDay] for the daily mix, by [period] if seasonal. */
    fun forKind(
        kind: GeneratedKind,
        epochDay: Long,
        period: CalendarPeriod? = null,
    ): Int =
        when (kind) {
            GeneratedKind.ON_REPEAT -> ON_REPEAT
            GeneratedKind.FORGOTTEN_FAVORITES -> FORGOTTEN_FAVORITES
            GeneratedKind.BEST_RATED -> BEST_RATED
            GeneratedKind.RECENTLY_ADDED -> RECENTLY_ADDED
            GeneratedKind.DAILY_MIX -> forDay(epochDay)
            GeneratedKind.MOST_PLAYED_SEASON, GeneratedKind.MOST_PLAYED_YEAR -> period?.let(::forPeriod) ?: YEAR
        }

    /** The pair of a seasonal set: one per season and one for the year. */
    fun forPeriod(period: CalendarPeriod): Int =
        when (period) {
            is CalendarPeriod.SeasonOf ->
                when (period.season) {
                    Season.WINTER -> WINTER
                    Season.SPRING -> SPRING
                    Season.SUMMER -> SUMMER
                    Season.AUTUMN -> AUTUMN
                }
            else -> YEAR
        }

    /** The pair of the daily mix: changes with the day. */
    fun forDay(epochDay: Long): Int = Math.floorMod(epochDay, COUNT.toLong()).toInt()
}

/**
 * Today's set of one kind, as the home screen shows it.
 *
 * @property period the calendar period of a seasonal set ([GeneratedKind.isSeasonal]); null for the others.
 * @property trackCount tracks of the set whose files are present.
 * @property firstTrackId the first of them, for the queue; the tile shows the gradient, not a cover.
 * @property lastStartedAtMs the latest listen from this set (`listen_event.source_kind`), for "Continue listening";
 *   null when nothing from it was played yet.
 */
data class GeneratedPlaylist(
    val kind: GeneratedKind,
    val epochDay: Long,
    val gradient: Int,
    val trackCount: Int,
    val createdAtMs: Long,
    val lastStartedAtMs: Long? = null,
    val period: CalendarPeriod? = null,
) {
    /** A set with too few tracks stays hidden; a seasonal one shows with any track. */
    val isShown: Boolean
        get() = trackCount >= if (kind.isSeasonal) 1 else GeneratedSets.MIN_SHOWN
}

/** The generated playlists of the home screen. Implemented in `core:library`. */
interface GeneratedPlaylistRepository {
    /**
     * Builds the sets of today unless they are built already: the first call of a new day rebuilds them. A seasonal
     * set is built while its period shows ([SeasonalWindow.needsBuild]).
     */
    suspend fun ensureToday()

    /**
     * Today's sets that are shown ([GeneratedPlaylist.isShown]) and seasonal sets in their window, in the order of
     * [GeneratedKind].
     */
    fun observePlaylists(): Flow<List<GeneratedPlaylist>>

    fun observePlaylist(kind: GeneratedKind): Flow<GeneratedPlaylist?>

    /** The present tracks of today's set of [kind], in order. */
    fun observeTracks(kind: GeneratedKind): Flow<List<Track>>

    /**
     * Keeps today's set of [kind] as a playlist of the user named [name], numbered when the name is taken
     * ([PlaylistNames.numbered]), with the gradient of the set as its cover.
     */
    suspend fun saveSnapshot(
        kind: GeneratedKind,
        name: String,
    ): SavedSnapshot
}

/** The playlist "Add" made from a generated playlist, with the name it got. */
data class SavedSnapshot(
    val playlistId: Long,
    val name: String,
)
