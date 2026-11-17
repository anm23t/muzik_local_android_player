package org.muzzzlo.player.feature.playlists

import org.muzzzlo.player.core.model.RuleField
import org.muzzzlo.player.core.model.RuleMatch
import org.muzzzlo.player.core.model.RuleOperator
import org.muzzzlo.player.core.model.RuleSortField

/** One change of a [SmartDraft] in the rules editor (task 2.5.10); [applyTo] makes the new draft. */
sealed interface DraftEdit {
    fun applyTo(draft: SmartDraft): SmartDraft

    data class Name(val name: String) : DraftEdit {
        override fun applyTo(draft: SmartDraft) = draft.copy(name = name)
    }

    /** How the groups combine. */
    data class Match(val match: RuleMatch) : DraftEdit {
        override fun applyTo(draft: SmartDraft) = draft.copy(match = match)
    }

    data object AddGroup : DraftEdit {
        override fun applyTo(draft: SmartDraft) = draft.withGroup()
    }

    data class RemoveGroup(val groupId: Long) : DraftEdit {
        override fun applyTo(draft: SmartDraft) = draft.copy(groups = draft.groups.filterNot { it.id == groupId })
    }

    /** How the conditions of one group combine. */
    data class GroupMatch(val groupId: Long, val match: RuleMatch) : DraftEdit {
        override fun applyTo(draft: SmartDraft) = draft.updateGroup(groupId) { it.copy(match = match) }
    }

    data class AddCondition(val groupId: Long) : DraftEdit {
        override fun applyTo(draft: SmartDraft) =
            draft.updateGroup(groupId) { it.copy(conditions = it.conditions + ConditionDraft(draft.nextId)) }
                .copy(nextId = draft.nextId + 1)
    }

    /** Removes a condition; a group left without conditions goes with it. */
    data class RemoveCondition(val groupId: Long, val conditionId: Long) : DraftEdit {
        override fun applyTo(draft: SmartDraft): SmartDraft {
            val edited =
                draft.updateGroup(groupId) { group ->
                    group.copy(
                        conditions =
                            group.conditions.filterNot {
                                it.id == conditionId
                            },
                    )
                }
            return edited.copy(groups = edited.groups.filter { it.conditions.isNotEmpty() })
        }
    }

    /** Another field: its first operator and an empty value, because the old value means nothing to it. */
    data class Field(val groupId: Long, val conditionId: Long, val field: RuleField) : DraftEdit {
        override fun applyTo(draft: SmartDraft) =
            draft.updateCondition(groupId, conditionId) {
                if (it.field == field) it else ConditionDraft(it.id, field, field.operators.first())
            }
    }

    data class Operator(val groupId: Long, val conditionId: Long, val operator: RuleOperator) : DraftEdit {
        override fun applyTo(draft: SmartDraft) =
            draft.updateCondition(groupId, conditionId) { it.copy(operator = operator) }
    }

    data class Value(val groupId: Long, val conditionId: Long, val value: String) : DraftEdit {
        override fun applyTo(draft: SmartDraft) = draft.updateCondition(groupId, conditionId) { it.copy(value = value) }
    }

    /** The second end of "between". */
    data class UpTo(val groupId: Long, val conditionId: Long, val upTo: String) : DraftEdit {
        override fun applyTo(draft: SmartDraft) = draft.updateCondition(groupId, conditionId) { it.copy(upTo = upTo) }
    }

    data class Sort(val field: RuleSortField, val descending: Boolean) : DraftEdit {
        override fun applyTo(draft: SmartDraft) =
            draft.copy(sort = draft.sort.copy(field = field, descending = descending))
    }

    data class Limit(val limit: String) : DraftEdit {
        override fun applyTo(draft: SmartDraft) = draft.copy(limit = limit.filter(Char::isDigit))
    }
}

private fun SmartDraft.updateGroup(
    groupId: Long,
    change: (GroupDraft) -> GroupDraft,
): SmartDraft = copy(groups = groups.map { if (it.id == groupId) change(it) else it })

private fun SmartDraft.updateCondition(
    groupId: Long,
    conditionId: Long,
    change: (ConditionDraft) -> ConditionDraft,
): SmartDraft =
    updateGroup(groupId) { group ->
        group.copy(conditions = group.conditions.map { if (it.id == conditionId) change(it) else it })
    }
