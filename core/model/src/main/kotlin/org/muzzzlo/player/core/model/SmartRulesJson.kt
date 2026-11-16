package org.muzzzlo.player.core.model

/**
 * The stored form of [SmartRules] (`smart_playlist.rules_json` and the backup, task 2.5.6):
 *
 * ```
 * {"v":1,"match":"all","groups":[{"match":"any","conditions":[{"field":"rating","op":"gte","number":80}]}],
 *  "sort":{"field":"playCount","desc":true},"limit":50}
 * ```
 *
 * Reading is forgiving, so a backup of a newer version still loads: a condition with an unknown field or operator,
 * or with a value it needs missing, is dropped; an unknown match reads as "all", an unknown sort as by title, a limit
 * below 1 as none.
 */
object SmartRulesJson {
    const val VERSION = 1

    fun encode(rules: SmartRules): String = Json.write(toMap(rules))

    /** The rules in [text]; null when the text is not JSON of the right shape at all. */
    fun decode(text: String): SmartRules? =
        runCatching { Json.parse(text) }.getOrNull()?.let { it as? Map<*, *> }?.let(::fromMap)

    fun toMap(rules: SmartRules): Map<String, Any?> =
        mapOf(
            "v" to VERSION,
            "match" to rules.match.key,
            "groups" to
                rules.groups.map { group ->
                    mapOf("match" to group.match.key, "conditions" to group.conditions.map(::conditionMap))
                },
            "sort" to mapOf("field" to rules.sort.field.key, "desc" to rules.sort.descending),
            "limit" to rules.limit,
        )

    fun fromMap(map: Map<*, *>): SmartRules {
        val sort = map["sort"] as? Map<*, *>
        return SmartRules(
            match = matchOf(map["match"]),
            groups = (map["groups"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map(::groupOf),
            sort =
                RuleSort(
                    field = RuleSortField.fromKey(sort?.get("field") as? String) ?: RuleSortField.TITLE,
                    descending = sort?.get("desc") as? Boolean ?: false,
                ),
            limit = map.long("limit")?.toInt()?.takeIf { it > 0 },
        )
    }

    private fun conditionMap(condition: RuleCondition): Map<String, Any?> =
        buildMap {
            put("field", condition.field.key)
            put("op", condition.operator.key)
            condition.number?.let { put("number", it) }
            condition.upTo?.let { put("upTo", it) }
            condition.text?.let { put("text", it) }
        }

    private fun groupOf(map: Map<*, *>) =
        RuleGroup(
            match = matchOf(map["match"]),
            conditions =
                (map["conditions"] as? List<*>).orEmpty()
                    .filterIsInstance<Map<*, *>>()
                    .mapNotNull(::conditionOf),
        )

    private fun conditionOf(map: Map<*, *>): RuleCondition? {
        val field = RuleField.fromKey(map["field"] as? String) ?: return null
        val operator = RuleOperator.fromKey(map["op"] as? String) ?: return null
        return RuleCondition(field, operator, map.long("number"), map.long("upTo"), map["text"] as? String)
            .takeIf { it.isValid }
    }

    private fun matchOf(value: Any?): RuleMatch = RuleMatch.entries.firstOrNull { it.key == value } ?: RuleMatch.ALL
}
