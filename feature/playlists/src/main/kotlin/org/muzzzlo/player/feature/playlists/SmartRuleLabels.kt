package org.muzzzlo.player.feature.playlists

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.muzzzlo.player.core.model.RuleField
import org.muzzzlo.player.core.model.RuleMatch
import org.muzzzlo.player.core.model.RuleOperator
import org.muzzzlo.player.core.model.RuleSortField
import org.muzzzlo.player.core.model.RuleValueKind

/** The name of a field in the rules editor (task 2.5.10). */
@Composable
internal fun fieldLabel(field: RuleField): String =
    stringResource(
        when (field) {
            RuleField.RATING -> R.string.rule_field_rating
            RuleField.FAVORITE -> R.string.rule_field_favorite
            RuleField.PLAY_COUNT -> R.string.rule_field_play_count
            RuleField.LAST_PLAYED -> R.string.rule_field_last_played
            RuleField.ARTIST -> R.string.rule_field_artist
            RuleField.ALBUM -> R.string.rule_field_album
            RuleField.GENRE -> R.string.rule_field_genre
            RuleField.YEAR -> R.string.rule_field_year
            RuleField.DATE_ADDED -> R.string.rule_field_date_added
            RuleField.DURATION -> R.string.rule_field_duration
        },
    )

/** The name of an operator; a favorite condition reads "yes" or "no". */
@Composable
internal fun operatorLabel(
    operator: RuleOperator,
    field: RuleField,
): String =
    if (field.kind == RuleValueKind.FLAG) {
        stringResource(if (operator == RuleOperator.IS) R.string.rule_operator_yes else R.string.rule_operator_no)
    } else {
        stringResource(operatorText(operator))
    }

private fun operatorText(operator: RuleOperator): Int =
    when (operator) {
        RuleOperator.IS -> R.string.rule_operator_is
        RuleOperator.IS_NOT -> R.string.rule_operator_is_not
        RuleOperator.GREATER -> R.string.rule_operator_greater
        RuleOperator.AT_LEAST -> R.string.rule_operator_at_least
        RuleOperator.LESS -> R.string.rule_operator_less
        RuleOperator.AT_MOST -> R.string.rule_operator_at_most
        RuleOperator.BETWEEN -> R.string.rule_operator_between
        RuleOperator.CONTAINS -> R.string.rule_operator_contains
        RuleOperator.NOT_CONTAINS -> R.string.rule_operator_not_contains
        RuleOperator.IN_LAST_DAYS -> R.string.rule_operator_in_last_days
        RuleOperator.NOT_IN_LAST_DAYS -> R.string.rule_operator_not_in_last_days
        RuleOperator.IS_SET -> R.string.rule_operator_is_set
        RuleOperator.IS_NOT_SET -> R.string.rule_operator_is_not_set
    }

/** The name of a sort field. */
@Composable
internal fun sortLabel(field: RuleSortField): String =
    stringResource(
        when (field) {
            RuleSortField.TITLE -> R.string.rule_field_title
            RuleSortField.ARTIST -> R.string.rule_field_artist
            RuleSortField.ALBUM -> R.string.rule_field_album
            RuleSortField.RATING -> R.string.rule_field_rating
            RuleSortField.PLAY_COUNT -> R.string.rule_field_play_count
            RuleSortField.LAST_PLAYED -> R.string.rule_field_last_played
            RuleSortField.DATE_ADDED -> R.string.rule_field_date_added
            RuleSortField.YEAR -> R.string.rule_field_year
            RuleSortField.DURATION -> R.string.rule_field_duration
            RuleSortField.FAVORITED_AT -> R.string.rule_field_favorited_at
        },
    )

@Composable
internal fun matchLabel(match: RuleMatch): String =
    stringResource(if (match == RuleMatch.ALL) R.string.smart_editor_match_all else R.string.smart_editor_match_any)

/** The label of the value field of a number or date condition: days, minutes or the field itself. */
@Composable
internal fun valueLabel(field: RuleField): String =
    when (field) {
        RuleField.LAST_PLAYED, RuleField.DATE_ADDED -> stringResource(R.string.smart_editor_days)
        RuleField.DURATION -> stringResource(R.string.smart_editor_minutes)
        else -> stringResource(R.string.smart_editor_value)
    }

/** The names a text condition offers: what the library has for [field]. */
internal fun RuleChoices.namesFor(field: RuleField): List<String> =
    when (field) {
        RuleField.ARTIST -> artists
        RuleField.ALBUM -> albums
        RuleField.GENRE -> genres
        else -> emptyList()
    }
