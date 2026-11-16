package org.muzzzlo.player.core.model

/** Checks one [RuleCondition] against one track (task 2.5.6). */
internal object RuleEvaluation {
    private const val MS_PER_DAY = 24L * 60 * 60 * 1000
    private const val MS_PER_SECOND = 1000L

    fun matches(
        condition: RuleCondition,
        fact: TrackFacts,
        nowMs: Long,
    ): Boolean =
        when (condition.field.kind) {
            RuleValueKind.NUMBER -> number(condition, numberOf(condition.field, fact))
            RuleValueKind.FLAG -> flag(condition, fact.isFavorite)
            RuleValueKind.TEXT -> text(condition, textsOf(condition.field, fact))
            RuleValueKind.DATE -> date(condition, dateOf(condition.field, fact), nowMs)
        }

    private fun numberOf(
        field: RuleField,
        fact: TrackFacts,
    ): Long? =
        when (field) {
            RuleField.RATING -> fact.track.ratingTenths?.toLong()
            RuleField.PLAY_COUNT -> fact.playCount.toLong()
            RuleField.YEAR -> fact.year?.toLong()
            RuleField.DURATION -> fact.track.durationMs / MS_PER_SECOND
            else -> null
        }

    private fun textsOf(
        field: RuleField,
        fact: TrackFacts,
    ): List<String> =
        when (field) {
            RuleField.ARTIST -> fact.track.artists
            RuleField.ALBUM -> listOfNotNull(fact.track.albumTitle)
            RuleField.GENRE -> fact.genres
            else -> emptyList()
        }

    private fun dateOf(
        field: RuleField,
        fact: TrackFacts,
    ): Long? =
        when (field) {
            RuleField.LAST_PLAYED -> fact.lastPlayedAtMs
            RuleField.DATE_ADDED -> fact.dateAddedMs
            else -> null
        }

    private fun number(
        condition: RuleCondition,
        value: Long?,
    ): Boolean {
        val wanted = condition.number
        return when (condition.operator) {
            RuleOperator.IS_SET -> value != null
            RuleOperator.IS_NOT_SET -> value == null
            RuleOperator.IS_NOT -> value != wanted
            else -> value != null && wanted != null && compare(condition, value, wanted)
        }
    }

    private fun compare(
        condition: RuleCondition,
        value: Long,
        wanted: Long,
    ): Boolean =
        when (condition.operator) {
            RuleOperator.IS -> value == wanted
            RuleOperator.GREATER -> value > wanted
            RuleOperator.AT_LEAST -> value >= wanted
            RuleOperator.LESS -> value < wanted
            RuleOperator.AT_MOST -> value <= wanted
            RuleOperator.BETWEEN -> {
                val other = condition.upTo ?: wanted
                value in minOf(wanted, other)..maxOf(wanted, other)
            }
            else -> false
        }

    private fun flag(
        condition: RuleCondition,
        value: Boolean,
    ): Boolean =
        when (condition.operator) {
            RuleOperator.IS -> value
            RuleOperator.IS_NOT -> !value
            else -> false
        }

    /** Names compare by [SearchText.key]: case, spaces, `&` / `and`, `ё` and diacritics do not matter. */
    private fun text(
        condition: RuleCondition,
        values: List<String>,
    ): Boolean {
        val wanted = SearchText.key(condition.text.orEmpty())
        val keys = values.map(SearchText::key)
        return when (condition.operator) {
            RuleOperator.IS -> wanted in keys
            RuleOperator.IS_NOT -> wanted !in keys
            RuleOperator.CONTAINS -> keys.any { wanted in it }
            RuleOperator.NOT_CONTAINS -> keys.none { wanted in it }
            RuleOperator.IS_SET -> keys.isNotEmpty()
            RuleOperator.IS_NOT_SET -> keys.isEmpty()
            else -> false
        }
    }

    private fun date(
        condition: RuleCondition,
        valueMs: Long?,
        nowMs: Long,
    ): Boolean {
        val cutoff = nowMs - (condition.number ?: 0) * MS_PER_DAY
        return when (condition.operator) {
            RuleOperator.IN_LAST_DAYS -> valueMs != null && valueMs >= cutoff
            RuleOperator.NOT_IN_LAST_DAYS -> valueMs == null || valueMs < cutoff
            RuleOperator.IS_SET -> valueMs != null
            RuleOperator.IS_NOT_SET -> valueMs == null
            else -> false
        }
    }
}

/** The comparators of [RuleSort]. */
internal object RuleSorting {
    fun comparator(sort: RuleSort): Comparator<TrackFacts> {
        val ties = compareBy<TrackFacts> { it.titleKey }.thenBy { it.track.id }
        val primary: Comparator<TrackFacts> =
            when (sort.field) {
                RuleSortField.TITLE -> return if (sort.descending) ties.reversed() else ties
                RuleSortField.ARTIST -> keyed(sort) { it.track.artists.firstOrNull()?.let(NameNormalizer::key) }
                RuleSortField.ALBUM -> keyed(sort) { it.track.albumTitle?.let(NameNormalizer::key) }
                RuleSortField.RATING -> keyed(sort) { it.track.ratingTenths }
                RuleSortField.PLAY_COUNT -> keyed(sort) { it.playCount }
                RuleSortField.LAST_PLAYED -> keyed(sort) { it.lastPlayedAtMs }
                RuleSortField.DATE_ADDED -> keyed(sort) { it.dateAddedMs }
                RuleSortField.YEAR -> keyed(sort) { it.year }
                RuleSortField.DURATION -> keyed(sort) { it.track.durationMs }
                RuleSortField.FAVORITED_AT -> keyed(sort) { it.favoritedAtMs }
            }
        return primary.then(ties)
    }

    /** Sorts by [key] in the direction of [sort]; a null key goes last either way. */
    private fun <T : Comparable<T>> keyed(
        sort: RuleSort,
        key: (TrackFacts) -> T?,
    ): Comparator<TrackFacts> {
        val order: Comparator<T> = if (sort.descending) reverseOrder() else naturalOrder()
        return compareBy(nullsLast(order), key)
    }
}
