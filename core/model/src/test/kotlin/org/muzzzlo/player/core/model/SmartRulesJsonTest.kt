package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SmartRulesJsonTest {
    @Test
    fun `rules survive a round trip`() {
        val rules =
            SmartRules(
                match = RuleMatch.ANY,
                groups =
                    listOf(
                        RuleGroup(
                            RuleMatch.ALL,
                            listOf(
                                RuleCondition(RuleField.RATING, RuleOperator.BETWEEN, 50, 80),
                                RuleCondition(RuleField.ARTIST, RuleOperator.IS, text = "Tyler, The \"Creator\""),
                                RuleCondition(RuleField.FAVORITE, RuleOperator.IS_NOT),
                            ),
                        ),
                        RuleGroup(RuleMatch.ANY, listOf(RuleCondition(RuleField.LAST_PLAYED, RuleOperator.IS_NOT_SET))),
                    ),
                sort = RuleSort(RuleSortField.PLAY_COUNT, descending = true),
                limit = 50,
            )
        assertEquals(rules, SmartRulesJson.decode(SmartRulesJson.encode(rules)))
    }

    @Test
    fun `defaults survive a round trip`() {
        assertEquals(SmartRules(), SmartRulesJson.decode(SmartRulesJson.encode(SmartRules())))
    }

    @Test
    fun `unknown fields, operators and missing values drop the condition only`() {
        val text =
            """
            {"v":7,"match":"all","groups":[{"match":"any","conditions":[
              {"field":"mood","op":"is","text":"happy"},
              {"field":"rating","op":"near","number":5},
              {"field":"year","op":"between","number":1990},
              {"field":"genre","op":"is"},
              {"field":"year","op":"gte","number":2000,"extra":true}
            ]}],"newKey":[1,2]}
            """.trimIndent()
        val rules = SmartRulesJson.decode(text)
        assertEquals(
            listOf(RuleGroup(RuleMatch.ANY, listOf(RuleCondition(RuleField.YEAR, RuleOperator.AT_LEAST, 2000)))),
            rules?.groups,
        )
    }

    @Test
    fun `unknown match and sort read as defaults, a bad limit as none`() {
        val rules =
            SmartRulesJson.decode(
                """{"match":"some","groups":[],"sort":{"field":"mood","desc":"yes"},"limit":0}""",
            )
        assertEquals(SmartRules(), rules)
        assertNull(SmartRulesJson.decode("""{"limit":-5}""")?.limit)
    }

    @Test
    fun `text that is not a JSON object is not rules`() {
        assertNull(SmartRulesJson.decode("not json"))
        assertNull(SmartRulesJson.decode("[1,2]"))
        assertNull(SmartRulesJson.decode(""))
    }

    @Test
    fun `wrong shapes inside are skipped`() {
        val rules = SmartRulesJson.decode("""{"groups":[1,"x",{"conditions":"no"}]}""")
        assertEquals(listOf(RuleGroup()), rules?.groups)
    }
}
