package org.muzzzlo.player.core.model

/**
 * What a track looks like to the rules of smart playlists (task 2.5.6). One list of these is built from the database;
 * tracks whose files are missing are not in it.
 *
 * @property titleKey [NameNormalizer.key] of the title, the tie order of every sort.
 * @property playCount counted listens of the track.
 * @property lastPlayedAtMs the latest counted listen, epoch milliseconds; null when never played.
 * @property dateAddedMs when the file appeared on the device, epoch milliseconds; null when unknown.
 * @property favoritedAtMs when the track became a favorite; 0 when unknown, null when not a favorite.
 */
data class TrackFacts(
    val track: Track,
    val titleKey: String,
    val isFavorite: Boolean = false,
    val favoritedAtMs: Long? = null,
    val playCount: Int = 0,
    val lastPlayedAtMs: Long? = null,
    val genres: List<String> = emptyList(),
    val year: Int? = null,
    val dateAddedMs: Long? = null,
)

/** How conditions or groups combine: every one must hold ("All") or at least one ("Any"). */
enum class RuleMatch(val key: String) {
    ALL("all"),
    ANY("any"),
}

/** The kind of value a [RuleField] holds; it decides the operators the field takes. */
enum class RuleValueKind { NUMBER, FLAG, TEXT, DATE }

/**
 * A property of a track a condition looks at. Number units: rating in tenths (0..100, 67 is 6.7), duration in
 * seconds, year, play count. Dates take a number of days.
 */
enum class RuleField(
    val key: String,
    val kind: RuleValueKind,
) {
    RATING("rating", RuleValueKind.NUMBER),
    FAVORITE("favorite", RuleValueKind.FLAG),
    PLAY_COUNT("playCount", RuleValueKind.NUMBER),
    LAST_PLAYED("lastPlayed", RuleValueKind.DATE),
    ARTIST("artist", RuleValueKind.TEXT),
    ALBUM("album", RuleValueKind.TEXT),
    GENRE("genre", RuleValueKind.TEXT),
    YEAR("year", RuleValueKind.NUMBER),
    DATE_ADDED("dateAdded", RuleValueKind.DATE),
    DURATION("duration", RuleValueKind.NUMBER),
    ;

    /** The operators this field takes, in the order an editor offers them. */
    val operators: List<RuleOperator> get() = RuleOperator.entries.filter { kind in it.kinds }

    companion object {
        fun fromKey(key: String?): RuleField? = entries.firstOrNull { it.key == key }
    }
}

/**
 * How a condition compares. For a value the track lacks (no rating, year, album, genre, never played) every
 * operator is false except [IS_NOT], [NOT_CONTAINS], [NOT_IN_LAST_DAYS] and [IS_NOT_SET].
 */
enum class RuleOperator(
    val key: String,
    internal val kinds: Set<RuleValueKind>,
) {
    IS("is", setOf(RuleValueKind.NUMBER, RuleValueKind.FLAG, RuleValueKind.TEXT)),
    IS_NOT("isNot", setOf(RuleValueKind.NUMBER, RuleValueKind.FLAG, RuleValueKind.TEXT)),
    GREATER("gt", setOf(RuleValueKind.NUMBER)),
    AT_LEAST("gte", setOf(RuleValueKind.NUMBER)),
    LESS("lt", setOf(RuleValueKind.NUMBER)),
    AT_MOST("lte", setOf(RuleValueKind.NUMBER)),

    /** Both ends included; the ends may come in any order. */
    BETWEEN("between", setOf(RuleValueKind.NUMBER)),
    CONTAINS("contains", setOf(RuleValueKind.TEXT)),
    NOT_CONTAINS("notContains", setOf(RuleValueKind.TEXT)),

    /** Within the last N days, counted back from now. */
    IN_LAST_DAYS("inLastDays", setOf(RuleValueKind.DATE)),

    /** Not within the last N days, including never. */
    NOT_IN_LAST_DAYS("notInLastDays", setOf(RuleValueKind.DATE)),

    /** The track has a value: a rating, a year, an album, a genre, a listen, a date added. */
    IS_SET("isSet", setOf(RuleValueKind.NUMBER, RuleValueKind.TEXT, RuleValueKind.DATE)),
    IS_NOT_SET("isNotSet", setOf(RuleValueKind.NUMBER, RuleValueKind.TEXT, RuleValueKind.DATE)),
    ;

    companion object {
        fun fromKey(key: String?): RuleOperator? = entries.firstOrNull { it.key == key }
    }
}

/**
 * One condition. [number] is the number of a number field or the days of a date field, [upTo] the second end of
 * [RuleOperator.BETWEEN], [text] the name for an artist, album or genre. A favorite condition has no value:
 * [RuleOperator.IS] means a favorite, [RuleOperator.IS_NOT] not a favorite.
 */
data class RuleCondition(
    val field: RuleField,
    val operator: RuleOperator,
    val number: Long? = null,
    val upTo: Long? = null,
    val text: String? = null,
) {
    /** The operator fits the field and the value it needs is there; an invalid condition is skipped. */
    val isValid: Boolean
        get() {
            // `field` alone would be the backing field inside a getter.
            val kind = this.field.kind
            return kind in operator.kinds &&
                when {
                    operator == RuleOperator.IS_SET || operator == RuleOperator.IS_NOT_SET -> true
                    kind == RuleValueKind.FLAG -> true
                    kind == RuleValueKind.TEXT -> !text.isNullOrBlank()
                    operator == RuleOperator.BETWEEN -> number != null && upTo != null
                    else -> number != null
                }
        }
}

/** Conditions joined by [match]. A group without valid conditions is left out. */
data class RuleGroup(
    val match: RuleMatch = RuleMatch.ALL,
    val conditions: List<RuleCondition> = emptyList(),
)

/** What a smart playlist is sorted by. Ties go by title, then by track id. */
enum class RuleSortField(val key: String) {
    TITLE("title"),
    ARTIST("artist"),
    ALBUM("album"),
    RATING("rating"),
    PLAY_COUNT("playCount"),
    LAST_PLAYED("lastPlayed"),
    DATE_ADDED("dateAdded"),
    YEAR("year"),
    DURATION("duration"),

    /** When the track became a favorite; for the built-in "Favorites". */
    FAVORITED_AT("favoritedAt"),
    ;

    companion object {
        fun fromKey(key: String?): RuleSortField? = entries.firstOrNull { it.key == key }
    }
}

/** Sort of a smart playlist; values a track lacks go last in both directions. */
data class RuleSort(
    val field: RuleSortField = RuleSortField.TITLE,
    val descending: Boolean = false,
)

/**
 * The rules of a smart playlist: [groups] joined by [match] (one level of nesting), a [sort] and an optional [limit]
 * ("top 50 by plays"). With no valid condition at all every track matches.
 */
data class SmartRules(
    val match: RuleMatch = RuleMatch.ALL,
    val groups: List<RuleGroup> = emptyList(),
    val sort: RuleSort = RuleSort(),
    val limit: Int? = null,
) {
    /** The tracks of [facts] that match, sorted and cut to [limit]; [nowMs] is the "now" of date conditions. */
    fun select(
        facts: List<TrackFacts>,
        nowMs: Long,
    ): List<Track> {
        val active =
            groups.map { it.copy(conditions = it.conditions.filter(RuleCondition::isValid)) }
                .filter { it.conditions.isNotEmpty() }
        val matched = facts.filter { fact -> active.isEmpty() || active.matches(match) { it.matches(fact, nowMs) } }
        val sorted = matched.sortedWith(RuleSorting.comparator(sort))
        val limited = limit?.takeIf { it > 0 }?.let(sorted::take) ?: sorted
        return limited.map { it.track }
    }

    private fun RuleGroup.matches(
        fact: TrackFacts,
        nowMs: Long,
    ): Boolean = conditions.matches(match) { RuleEvaluation.matches(it, fact, nowMs) }

    private fun <T> List<T>.matches(
        match: RuleMatch,
        test: (T) -> Boolean,
    ): Boolean = if (match == RuleMatch.ALL) all(test) else any(test)

    companion object {
        /** Rules no track matches: what a stored playlist with unreadable rules shows. */
        val NOTHING =
            SmartRules(
                groups =
                    listOf(
                        RuleGroup(conditions = listOf(RuleCondition(RuleField.PLAY_COUNT, RuleOperator.LESS, 0))),
                    ),
            )
    }
}
