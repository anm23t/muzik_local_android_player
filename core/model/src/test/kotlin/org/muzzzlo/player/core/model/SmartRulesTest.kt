package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

private fun fact(
    id: Long,
    title: String = "Song $id",
    artists: List<String> = listOf("Artist"),
    album: String? = "Album",
    durationMs: Long = 200_000,
    rating: Int? = null,
    favorite: Boolean = false,
    favoritedAt: Long? = if (favorite) 0 else null,
    plays: Int = 0,
    lastPlayed: Long? = null,
    genres: List<String> = emptyList(),
    year: Int? = null,
    added: Long? = null,
) = TrackFacts(
    track =
        Track(
            id = id,
            uri = "content://$id",
            title = title,
            artists = artists,
            albumId = null,
            albumTitle = album,
            durationMs = durationMs,
            trackNumber = null,
            ratingTenths = rating,
        ),
    titleKey = NameNormalizer.key(title),
    isFavorite = favorite,
    favoritedAtMs = favoritedAt,
    playCount = plays,
    lastPlayedAtMs = lastPlayed,
    genres = genres,
    year = year,
    dateAddedMs = added,
)

class SmartRulesTest {
    private fun one(condition: RuleCondition) = SmartRules(groups = listOf(RuleGroup(conditions = listOf(condition))))

    private fun ids(
        rules: SmartRules,
        facts: List<TrackFacts>,
    ) = rules.select(facts, NOW).map { it.id }

    @ParameterizedTest(name = "{0}")
    @MethodSource("conditionCases")
    fun `condition table`(
        @Suppress("UNUSED_PARAMETER") case: String,
        condition: RuleCondition,
        fact: TrackFacts,
        expected: Boolean,
    ) {
        assertEquals(expected, ids(one(condition), listOf(fact)) == listOf(fact.track.id))
    }

    @Test
    fun `no conditions at all match every track`() {
        val facts = listOf(fact(1), fact(2))
        assertEquals(listOf(1L, 2L), ids(SmartRules(), facts))
        assertEquals(listOf(1L, 2L), ids(SmartRules(groups = listOf(RuleGroup())), facts))
    }

    @Test
    fun `invalid conditions are skipped, an empty group is left out`() {
        val rules =
            SmartRules(
                groups =
                    listOf(
                        RuleGroup(conditions = listOf(RuleCondition(RuleField.RATING, RuleOperator.CONTAINS, 5))),
                        RuleGroup(conditions = listOf(RuleCondition(RuleField.YEAR, RuleOperator.IS, 2020))),
                    ),
            )
        assertEquals(listOf(1L), ids(rules, listOf(fact(1, year = 2020), fact(2, year = 2021))))
    }

    @Test
    fun `all and any inside groups and between groups`() {
        val rated = RuleCondition(RuleField.RATING, RuleOperator.AT_LEAST, 80)
        val favorite = RuleCondition(RuleField.FAVORITE, RuleOperator.IS)
        val rock = RuleCondition(RuleField.GENRE, RuleOperator.IS, text = "Rock")
        val facts =
            listOf(
                fact(1, rating = 90, genres = listOf("Rock")),
                fact(2, favorite = true, genres = listOf("Pop")),
                fact(3, rating = 85, favorite = true, genres = listOf("Pop")),
                fact(4, genres = listOf("Rock")),
            )
        val anyThenRock =
            SmartRules(
                match = RuleMatch.ALL,
                groups =
                    listOf(
                        RuleGroup(RuleMatch.ANY, listOf(rated, favorite)),
                        RuleGroup(conditions = listOf(rock)),
                    ),
            )
        assertEquals(listOf(1L), ids(anyThenRock, facts))

        val allOrRock =
            SmartRules(
                match = RuleMatch.ANY,
                groups =
                    listOf(
                        RuleGroup(RuleMatch.ALL, listOf(rated, favorite)),
                        RuleGroup(conditions = listOf(rock)),
                    ),
            )
        assertEquals(listOf(1L, 3L, 4L), ids(allOrRock, facts))
    }

    @Test
    fun `top 2 by plays with ties by title`() {
        val rules = SmartRules(sort = RuleSort(RuleSortField.PLAY_COUNT, descending = true), limit = 2)
        val facts =
            listOf(fact(1, "B", plays = 5), fact(2, "A", plays = 5), fact(3, "C", plays = 9), fact(4, plays = 1))
        assertEquals(listOf(3L, 2L), ids(rules, facts))
    }

    @Test
    fun `a limit below one means no limit`() {
        val facts = listOf(fact(1), fact(2))
        assertEquals(2, ids(SmartRules(limit = 0), facts).size)
        assertEquals(2, ids(SmartRules(limit = -3), facts).size)
    }

    @Test
    fun `missing values sort last in both directions`() {
        val facts = listOf(fact(1, "A"), fact(2, "B", year = 1990), fact(3, "C", year = 2000))
        val down = SmartRules(sort = RuleSort(RuleSortField.YEAR, descending = true))
        val up = SmartRules(sort = RuleSort(RuleSortField.YEAR, descending = false))
        assertEquals(listOf(3L, 2L, 1L), ids(down, facts))
        assertEquals(listOf(2L, 3L, 1L), ids(up, facts))
    }

    @Test
    fun `every sort field orders the tracks`() {
        val facts =
            listOf(
                fact(
                    1, "A",
                    artists =
                        listOf(
                            "Zed",
                        ),
                    album = "Y", durationMs = 3000, rating = 10, plays = 1, lastPlayed = 5, year = 2001, added = 9,
                ),
                fact(
                    2, "B",
                    artists =
                        listOf(
                            "Abe",
                        ),
                    album = "X", durationMs = 1000, rating = 90, plays = 7, lastPlayed = 9, year = 1999, added = 1,
                ),
            )
        val expectedAscending =
            mapOf(
                RuleSortField.TITLE to listOf(1L, 2L),
                RuleSortField.ARTIST to listOf(2L, 1L),
                RuleSortField.ALBUM to listOf(2L, 1L),
                RuleSortField.RATING to listOf(1L, 2L),
                RuleSortField.PLAY_COUNT to listOf(1L, 2L),
                RuleSortField.LAST_PLAYED to listOf(1L, 2L),
                RuleSortField.DATE_ADDED to listOf(2L, 1L),
                RuleSortField.YEAR to listOf(2L, 1L),
                RuleSortField.DURATION to listOf(2L, 1L),
            )
        for ((field, expected) in expectedAscending) {
            assertEquals(expected, ids(SmartRules(sort = RuleSort(field)), facts), "$field ascending")
            assertEquals(expected.reversed(), ids(SmartRules(sort = RuleSort(field, true)), facts), "$field descending")
        }
    }

    @Test
    fun `built-in favorites keep the latest favorite first`() {
        val facts =
            listOf(
                fact(1, "A", favorite = true, favoritedAt = 100),
                fact(2, "B", favorite = true, favoritedAt = 300),
                fact(3, "C"),
                fact(4, "D", favorite = true, favoritedAt = 0),
                fact(5, "Aa", favorite = true, favoritedAt = 0),
            )
        assertEquals(listOf(2L, 1L, 5L, 4L), ids(AutoPlaylist.Favorites.rules, facts))
    }

    @Test
    fun `built-in top songs keep the highest rating first and leave unrated out`() {
        val facts = listOf(fact(1, "B", rating = 70), fact(2, "A", rating = 70), fact(3, rating = 95), fact(4))
        assertEquals(listOf(3L, 2L, 1L), ids(AutoPlaylist.TopSongs.rules, facts))
    }

    @Test
    fun `nothing matches nothing`() {
        assertEquals(emptyList<Long>(), ids(SmartRules.NOTHING, listOf(fact(1), fact(2, plays = 4))))
    }

    @Test
    fun `operators follow the kind of the field`() {
        assertTrue(RuleOperator.CONTAINS in RuleField.ARTIST.operators)
        assertFalse(RuleOperator.CONTAINS in RuleField.RATING.operators)
        assertEquals(listOf(RuleOperator.IS, RuleOperator.IS_NOT), RuleField.FAVORITE.operators)
        assertTrue(RuleOperator.IN_LAST_DAYS in RuleField.DATE_ADDED.operators)
    }

    companion object {
        private const val DAY = 24L * 60 * 60 * 1000
        private const val NOW = 1_000 * DAY

        private fun c(
            field: RuleField,
            operator: RuleOperator,
            number: Long? = null,
            upTo: Long? = null,
            text: String? = null,
        ) = RuleCondition(field, operator, number, upTo, text)

        @JvmStatic
        @Suppress("LongMethod")
        fun conditionCases(): List<Arguments> {
            fun case(
                name: String,
                condition: RuleCondition,
                fact: TrackFacts,
                expected: Boolean,
            ) = Arguments.of(name, condition, fact, expected)
            return listOf(
                // rating, in tenths
                case("rating is", c(RuleField.RATING, RuleOperator.IS, 67), fact(1, rating = 67), true),
                case("rating is, unrated", c(RuleField.RATING, RuleOperator.IS, 67), fact(1), false),
                case("rating is not, unrated", c(RuleField.RATING, RuleOperator.IS_NOT, 67), fact(1), true),
                case("rating is not, same", c(RuleField.RATING, RuleOperator.IS_NOT, 67), fact(1, rating = 67), false),
                case("rating > edge", c(RuleField.RATING, RuleOperator.GREATER, 80), fact(1, rating = 80), false),
                case("rating >= edge", c(RuleField.RATING, RuleOperator.AT_LEAST, 80), fact(1, rating = 80), true),
                case("rating < edge", c(RuleField.RATING, RuleOperator.LESS, 80), fact(1, rating = 80), false),
                case("rating <= edge", c(RuleField.RATING, RuleOperator.AT_MOST, 80), fact(1, rating = 80), true),
                case("rating < unrated", c(RuleField.RATING, RuleOperator.LESS, 80), fact(1), false),
                case("rating between", c(RuleField.RATING, RuleOperator.BETWEEN, 50, 70), fact(1, rating = 70), true),
                case(
                    "rating between reversed",
                    c(RuleField.RATING, RuleOperator.BETWEEN, 70, 50),
                    fact(1, rating = 50),
                    true,
                ),
                case(
                    "rating between outside",
                    c(RuleField.RATING, RuleOperator.BETWEEN, 50, 70),
                    fact(1, rating = 71),
                    false,
                ),
                case("rating set", c(RuleField.RATING, RuleOperator.IS_SET), fact(1, rating = 0), true),
                case("rating not set", c(RuleField.RATING, RuleOperator.IS_NOT_SET), fact(1), true),
                // favorite
                case("favorite is", c(RuleField.FAVORITE, RuleOperator.IS), fact(1, favorite = true), true),
                case("favorite is, not one", c(RuleField.FAVORITE, RuleOperator.IS), fact(1), false),
                case("favorite is not", c(RuleField.FAVORITE, RuleOperator.IS_NOT), fact(1), true),
                // play count
                case("never played, plays = 0", c(RuleField.PLAY_COUNT, RuleOperator.IS, 0), fact(1), true),
                case("plays >= 10", c(RuleField.PLAY_COUNT, RuleOperator.AT_LEAST, 10), fact(1, plays = 10), true),
                case("plays < 10", c(RuleField.PLAY_COUNT, RuleOperator.LESS, 10), fact(1, plays = 10), false),
                // last played, days
                case(
                    "played in last 7 days",
                    c(RuleField.LAST_PLAYED, RuleOperator.IN_LAST_DAYS, 7),
                    fact(1, lastPlayed = NOW - 7 * DAY),
                    true,
                ),
                case(
                    "played 8 days ago",
                    c(RuleField.LAST_PLAYED, RuleOperator.IN_LAST_DAYS, 7),
                    fact(1, lastPlayed = NOW - 8 * DAY),
                    false,
                ),
                case("never played, in last", c(RuleField.LAST_PLAYED, RuleOperator.IN_LAST_DAYS, 7), fact(1), false),
                case("not in last, never", c(RuleField.LAST_PLAYED, RuleOperator.NOT_IN_LAST_DAYS, 60), fact(1), true),
                case(
                    "not in last, old",
                    c(RuleField.LAST_PLAYED, RuleOperator.NOT_IN_LAST_DAYS, 60),
                    fact(1, lastPlayed = NOW - 61 * DAY),
                    true,
                ),
                case(
                    "not in last, recent",
                    c(RuleField.LAST_PLAYED, RuleOperator.NOT_IN_LAST_DAYS, 60),
                    fact(1, lastPlayed = NOW - DAY),
                    false,
                ),
                case("ever played", c(RuleField.LAST_PLAYED, RuleOperator.IS_SET), fact(1, lastPlayed = 1), true),
                case("never played", c(RuleField.LAST_PLAYED, RuleOperator.IS_NOT_SET), fact(1), true),
                // artist, each artist of the track on its own
                case(
                    "artist is second",
                    c(RuleField.ARTIST, RuleOperator.IS, text = "pusha t"),
                    fact(1, artists = listOf("Kanye West", "Pusha T")),
                    true,
                ),
                case(
                    "artist is, part only",
                    c(RuleField.ARTIST, RuleOperator.IS, text = "Pusha"),
                    fact(1, artists = listOf("Pusha T")),
                    false,
                ),
                case(
                    "artist is, ё",
                    c(RuleField.ARTIST, RuleOperator.IS, text = "Земфира"),
                    fact(1, artists = listOf("Зёмфира")),
                    true,
                ),
                case(
                    "artist is, and",
                    c(RuleField.ARTIST, RuleOperator.IS, text = "Simon and Garfunkel"),
                    fact(1, artists = listOf("Simon & Garfunkel")),
                    true,
                ),
                case(
                    "artist is not",
                    c(RuleField.ARTIST, RuleOperator.IS_NOT, text = "A"),
                    fact(1, artists = listOf("A", "B")),
                    false,
                ),
                case(
                    "artist is not, no artist",
                    c(RuleField.ARTIST, RuleOperator.IS_NOT, text = "A"),
                    fact(1, artists = emptyList()),
                    true,
                ),
                case(
                    "artist contains",
                    c(RuleField.ARTIST, RuleOperator.CONTAINS, text = "west"),
                    fact(1, artists = listOf("Kanye West")),
                    true,
                ),
                case(
                    "artist not contains",
                    c(RuleField.ARTIST, RuleOperator.NOT_CONTAINS, text = "west"),
                    fact(1, artists = listOf("Kanye West")),
                    false,
                ),
                case("artist set", c(RuleField.ARTIST, RuleOperator.IS_SET), fact(1, artists = emptyList()), false),
                // album
                case("album is", c(RuleField.ALBUM, RuleOperator.IS, text = "  album "), fact(1), true),
                case(
                    "album is, no album",
                    c(RuleField.ALBUM, RuleOperator.IS, text = "Album"),
                    fact(1, album = null),
                    false,
                ),
                case(
                    "album is not, no album",
                    c(RuleField.ALBUM, RuleOperator.IS_NOT, text = "Album"),
                    fact(1, album = null),
                    true,
                ),
                case("album not set", c(RuleField.ALBUM, RuleOperator.IS_NOT_SET), fact(1, album = null), true),
                case(
                    "album blank text invalid: all match",
                    c(RuleField.ALBUM, RuleOperator.IS, text = " "),
                    fact(1, album = null),
                    true,
                ),
                // genre
                case(
                    "genre is one of",
                    c(RuleField.GENRE, RuleOperator.IS, text = "pop"),
                    fact(1, genres = listOf("Rock", "Pop")),
                    true,
                ),
                case("genre is, no genre", c(RuleField.GENRE, RuleOperator.IS, text = "Pop"), fact(1), false),
                case("genre is not, no genre", c(RuleField.GENRE, RuleOperator.IS_NOT, text = "Pop"), fact(1), true),
                case(
                    "genre contains",
                    c(RuleField.GENRE, RuleOperator.CONTAINS, text = "rock"),
                    fact(1, genres = listOf("Indie Rock")),
                    true,
                ),
                case("genre not set", c(RuleField.GENRE, RuleOperator.IS_NOT_SET), fact(1), true),
                // year
                case("year between", c(RuleField.YEAR, RuleOperator.BETWEEN, 1990, 1999), fact(1, year = 1995), true),
                case("year between, no year", c(RuleField.YEAR, RuleOperator.BETWEEN, 1990, 1999), fact(1), false),
                case("year is not, no year", c(RuleField.YEAR, RuleOperator.IS_NOT, 2000), fact(1), true),
                case("year not set", c(RuleField.YEAR, RuleOperator.IS_NOT_SET), fact(1), true),
                // date added, days
                case(
                    "added in last 30",
                    c(RuleField.DATE_ADDED, RuleOperator.IN_LAST_DAYS, 30),
                    fact(1, added = NOW - DAY),
                    true,
                ),
                case(
                    "added long ago",
                    c(RuleField.DATE_ADDED, RuleOperator.IN_LAST_DAYS, 30),
                    fact(1, added = NOW - 31 * DAY),
                    false,
                ),
                case("added unknown", c(RuleField.DATE_ADDED, RuleOperator.IN_LAST_DAYS, 30), fact(1), false),
                case(
                    "not added lately, unknown",
                    c(RuleField.DATE_ADDED, RuleOperator.NOT_IN_LAST_DAYS, 30),
                    fact(1),
                    true,
                ),
                // duration, seconds
                case(
                    "longer than 5 min",
                    c(RuleField.DURATION, RuleOperator.GREATER, 300),
                    fact(1, durationMs = 300_999),
                    false,
                ),
                case(
                    "at least 5 min",
                    c(RuleField.DURATION, RuleOperator.AT_LEAST, 300),
                    fact(1, durationMs = 300_000),
                    true,
                ),
                case(
                    "shorter than 2 min",
                    c(RuleField.DURATION, RuleOperator.LESS, 120),
                    fact(1, durationMs = 119_000),
                    true,
                ),
            )
        }
    }
}
