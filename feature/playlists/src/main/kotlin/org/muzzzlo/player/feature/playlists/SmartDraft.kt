package org.muzzzlo.player.feature.playlists

import org.muzzzlo.player.core.model.RuleCondition
import org.muzzzlo.player.core.model.RuleField
import org.muzzzlo.player.core.model.RuleGroup
import org.muzzzlo.player.core.model.RuleMatch
import org.muzzzlo.player.core.model.RuleOperator
import org.muzzzlo.player.core.model.RuleSort
import org.muzzzlo.player.core.model.RuleSortField
import org.muzzzlo.player.core.model.RuleValueKind
import org.muzzzlo.player.core.model.SmartRules
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * One condition while it is edited (task 2.5.10). Values stay text as the user types them: a rating as "7.5", a
 * duration in minutes, dates as a number of days; [toCondition] turns them into a [RuleCondition].
 *
 * @property id keeps the row in place in the list while others are added and removed.
 */
data class ConditionDraft(
    val id: Long,
    val field: RuleField = RuleField.ARTIST,
    val operator: RuleOperator = RuleField.ARTIST.operators.first(),
    val value: String = "",
    val upTo: String = "",
) {
    /** The operator needs no value: a favorite flag or "is set" / "is not set". */
    val needsValue: Boolean
        // `field` alone would be the backing field inside a getter.
        get() =
            this.field.kind != RuleValueKind.FLAG &&
                operator != RuleOperator.IS_SET &&
                operator != RuleOperator.IS_NOT_SET

    /** The condition as the rules keep it; null while its value cannot be read. */
    fun toCondition(): RuleCondition? {
        val condition =
            when {
                !needsValue -> RuleCondition(field, operator)
                field.kind == RuleValueKind.TEXT -> RuleCondition(field, operator, text = value.trim())
                else -> {
                    val upper = if (operator == RuleOperator.BETWEEN) parseNumber(field, upTo) else null
                    RuleCondition(field, operator, parseNumber(field, value), upper)
                }
            }
        return condition.takeIf { it.isValid }
    }

    companion object {
        /** The draft of a stored condition, values written back the way the editor shows them. */
        fun of(
            id: Long,
            condition: RuleCondition,
        ) = ConditionDraft(
            id = id,
            field = condition.field,
            operator = condition.operator,
            value = condition.text ?: formatNumber(condition.field, condition.number),
            upTo = formatNumber(condition.field, condition.upTo),
        )
    }
}

/** A group of conditions joined by [match]. */
data class GroupDraft(
    val id: Long,
    val match: RuleMatch = RuleMatch.ALL,
    val conditions: List<ConditionDraft> = emptyList(),
)

/**
 * A smart playlist while it is edited: its name and rules as text fields and lists (task 2.5.10).
 *
 * @property limit the limit as typed; empty means no limit.
 * @property nextId the id of the next group or condition, so ids never repeat in one draft.
 */
data class SmartDraft(
    val name: String = "",
    val match: RuleMatch = RuleMatch.ALL,
    val groups: List<GroupDraft> = emptyList(),
    val sort: RuleSort = RuleSort(RuleSortField.TITLE),
    val limit: String = "",
    val nextId: Long = 0,
) {
    /** Conditions whose value can be read; the editor asks for at least one. */
    val validConditionCount: Int get() = groups.sumOf { group -> group.conditions.count { it.toCondition() != null } }

    /** The rules of the draft: unreadable conditions and empty groups are left out. */
    fun toRules(): SmartRules =
        SmartRules(
            match = match,
            groups =
                groups.map { group -> RuleGroup(group.match, group.conditions.mapNotNull { it.toCondition() }) }
                    .filter { it.conditions.isNotEmpty() },
            sort = sort,
            limit = limit.trim().toIntOrNull()?.takeIf { it > 0 },
        )

    companion object {
        /** A new smart playlist: one group with one empty condition. */
        fun new(): SmartDraft = SmartDraft().withGroup()

        /** The draft of a stored smart playlist. */
        fun of(
            name: String,
            rules: SmartRules,
        ): SmartDraft {
            var id = 0L
            val groups =
                rules.groups.map { group ->
                    GroupDraft(id++, group.match, group.conditions.map { ConditionDraft.of(id++, it) })
                }
            return SmartDraft(
                name = name,
                match = rules.match,
                groups = groups,
                sort = rules.sort,
                limit = rules.limit?.toString().orEmpty(),
                nextId = id,
            )
        }
    }
}

/** Adds a group with one empty condition. */
internal fun SmartDraft.withGroup(): SmartDraft =
    copy(groups = groups + GroupDraft(nextId, conditions = listOf(ConditionDraft(nextId + 1))), nextId = nextId + 2)

private const val SECONDS_PER_MINUTE = 60
private const val TENTHS = 10

/** How many units of the stored number one shown unit holds: ratings in tenths, durations in seconds. */
private fun scaleOf(field: RuleField): Int =
    when (field) {
        RuleField.RATING -> TENTHS
        RuleField.DURATION -> SECONDS_PER_MINUTE
        else -> 1
    }

/** A shown number ("7,5", "3.5") as the stored one (75 tenths, 210 seconds); null for anything else. */
internal fun parseNumber(
    field: RuleField,
    text: String,
): Long? {
    val number = text.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
    return number.multiply(BigDecimal(scaleOf(field))).setScale(0, RoundingMode.HALF_UP).toLong()
}

/** A stored number as the editor shows it: "7.5" for 75 tenths, "3.5" for 210 seconds, "" for none. */
internal fun formatNumber(
    field: RuleField,
    number: Long?,
): String {
    number ?: return ""
    val scale = scaleOf(field)
    if (scale == 1) return number.toString()
    return BigDecimal(number).divide(BigDecimal(scale), 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}
