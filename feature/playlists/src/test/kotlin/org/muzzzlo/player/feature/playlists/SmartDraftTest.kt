package org.muzzzlo.player.feature.playlists

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.muzzzlo.player.core.model.RuleCondition
import org.muzzzlo.player.core.model.RuleField
import org.muzzzlo.player.core.model.RuleGroup
import org.muzzzlo.player.core.model.RuleMatch
import org.muzzzlo.player.core.model.RuleOperator
import org.muzzzlo.player.core.model.RuleSort
import org.muzzzlo.player.core.model.RuleSortField
import org.muzzzlo.player.core.model.SmartRules

class SmartDraftTest {
    @ParameterizedTest(name = "{0} \"{1}\" is {2}")
    @CsvSource(
        "RATING, 7.5, 75",
        "RATING, '7,5', 75",
        "RATING, 10, 100",
        "RATING, 6.66, 67",
        "DURATION, 3.5, 210",
        "DURATION, 4, 240",
        "YEAR, 1999, 1999",
        "PLAY_COUNT, ' 12 ', 12",
    )
    fun `shown numbers become stored ones`(
        field: RuleField,
        text: String,
        stored: Long,
    ) {
        assertEquals(stored, parseNumber(field, text))
    }

    @Test
    fun `text that is not a number has no value`() {
        listOf("", " ", "abc", "1.2.3").forEach { assertNull(parseNumber(RuleField.YEAR, it), it) }
    }

    @Test
    fun `stored numbers are shown back without extra zeros`() {
        assertEquals("7.5", formatNumber(RuleField.RATING, 75))
        assertEquals("8", formatNumber(RuleField.RATING, 80))
        assertEquals("3.5", formatNumber(RuleField.DURATION, 210))
        assertEquals("1999", formatNumber(RuleField.YEAR, 1999))
        assertEquals("", formatNumber(RuleField.YEAR, null))
    }

    @Test
    fun `incomplete conditions and empty groups are left out of the rules`() {
        val draft =
            SmartDraft(
                groups =
                    listOf(
                        GroupDraft(
                            1,
                            RuleMatch.ANY,
                            listOf(
                                ConditionDraft(2, RuleField.ARTIST, RuleOperator.IS, value = " Bjork "),
                                ConditionDraft(3, RuleField.ALBUM, RuleOperator.IS, value = " "),
                                ConditionDraft(4, RuleField.RATING, RuleOperator.BETWEEN, value = "7", upTo = ""),
                            ),
                        ),
                        GroupDraft(5, conditions = listOf(ConditionDraft(6, RuleField.YEAR, RuleOperator.LESS, "x"))),
                    ),
                limit = "0",
            )
        val rules = draft.toRules()

        assertEquals(1, draft.validConditionCount)
        assertEquals(
            listOf(RuleGroup(RuleMatch.ANY, listOf(RuleCondition(RuleField.ARTIST, RuleOperator.IS, text = "Bjork")))),
            rules.groups,
        )
        assertNull(rules.limit)
    }

    @Test
    fun `flags and set checks need no value, between keeps both ends`() {
        assertEquals(
            RuleCondition(RuleField.FAVORITE, RuleOperator.IS_NOT),
            ConditionDraft(1, RuleField.FAVORITE, RuleOperator.IS_NOT).toCondition(),
        )
        assertEquals(
            RuleCondition(RuleField.YEAR, RuleOperator.IS_NOT_SET),
            ConditionDraft(1, RuleField.YEAR, RuleOperator.IS_NOT_SET, value = "junk").toCondition(),
        )
        assertEquals(
            RuleCondition(RuleField.RATING, RuleOperator.BETWEEN, 70, 85),
            ConditionDraft(1, RuleField.RATING, RuleOperator.BETWEEN, "7", "8.5").toCondition(),
        )
        assertEquals(
            RuleCondition(RuleField.RATING, RuleOperator.AT_LEAST, 70),
            ConditionDraft(1, RuleField.RATING, RuleOperator.AT_LEAST, "7", upTo = "9").toCondition(),
        )
    }

    @Test
    fun `stored rules come back to the same rules through the draft`() {
        val rules =
            SmartRules(
                match = RuleMatch.ANY,
                groups =
                    listOf(
                        RuleGroup(
                            RuleMatch.ALL,
                            listOf(
                                RuleCondition(RuleField.GENRE, RuleOperator.CONTAINS, text = "jazz"),
                                RuleCondition(RuleField.DURATION, RuleOperator.AT_MOST, 330),
                            ),
                        ),
                        RuleGroup(
                            RuleMatch.ANY,
                            listOf(RuleCondition(RuleField.LAST_PLAYED, RuleOperator.IN_LAST_DAYS, 30)),
                        ),
                    ),
                sort = RuleSort(RuleSortField.PLAY_COUNT, descending = true),
                limit = 50,
            )
        val draft = SmartDraft.of("Jazz", rules)

        assertEquals("Jazz", draft.name)
        assertEquals("5.5", draft.groups[0].conditions[1].value)
        assertEquals("50", draft.limit)
        assertEquals(rules, draft.toRules())
        val ids = draft.groups.flatMap { listOf(it.id) + it.conditions.map(ConditionDraft::id) }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(ids.max() + 1, draft.nextId)
    }

    @Test
    fun `edits add and remove groups and conditions with new ids`() {
        var draft = SmartDraft.new()
        val first = draft.groups.single()
        draft = DraftEdit.AddCondition(first.id).applyTo(draft)
        draft = DraftEdit.AddGroup.applyTo(draft)
        val ids = draft.groups.flatMap { listOf(it.id) + it.conditions.map(ConditionDraft::id) }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(listOf(2, 1), draft.groups.map { it.conditions.size })

        val second = draft.groups[1]
        draft = DraftEdit.RemoveCondition(second.id, second.conditions.single().id).applyTo(draft)
        assertEquals(listOf(first.id), draft.groups.map { it.id })
        draft = DraftEdit.RemoveGroup(first.id).applyTo(draft)
        assertEquals(emptyList<GroupDraft>(), draft.groups)
    }

    @Test
    fun `another field resets the operator and the value, the same field keeps them`() {
        var draft = SmartDraft.new()
        val group = draft.groups.single()
        val condition = group.conditions.single()
        draft = DraftEdit.Value(group.id, condition.id, "Bjork").applyTo(draft)
        draft = DraftEdit.Field(group.id, condition.id, RuleField.ARTIST).applyTo(draft)
        assertEquals("Bjork", draft.groups.single().conditions.single().value)

        draft = DraftEdit.Field(group.id, condition.id, RuleField.RATING).applyTo(draft)
        val changed = draft.groups.single().conditions.single()
        assertEquals(RuleField.RATING.operators.first(), changed.operator)
        assertEquals("", changed.value)
    }

    @Test
    fun `the limit keeps digits only, the sort and the matches change`() {
        var draft = SmartDraft.new()
        draft = DraftEdit.Limit("2a5").applyTo(draft)
        draft = DraftEdit.Sort(RuleSortField.RATING, descending = true).applyTo(draft)
        draft = DraftEdit.Match(RuleMatch.ANY).applyTo(draft)
        draft = DraftEdit.GroupMatch(draft.groups.single().id, RuleMatch.ANY).applyTo(draft)
        assertEquals("25", draft.limit)
        assertEquals(RuleSort(RuleSortField.RATING, true), draft.sort)
        assertEquals(RuleMatch.ANY, draft.match)
        assertEquals(RuleMatch.ANY, draft.groups.single().match)
    }
}
