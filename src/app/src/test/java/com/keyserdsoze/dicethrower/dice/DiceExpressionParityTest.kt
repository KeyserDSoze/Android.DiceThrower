package com.keyserdsoze.dicethrower.dice

import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.LevelRuleKind
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollLevelRule
import com.keyserdsoze.dicethrower.model.RollSubgroup
import com.keyserdsoze.dicethrower.model.RollSubgroupOperator
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The browser test reads this exact resource; native production parsers remain the oracle. */
class DiceExpressionParityTest {
    private val corpus = JSONObject(checkNotNull(javaClass.getResourceAsStream("/dice-expression-parity.json"))
        .bufferedReader().use { it.readText() })
    private val heroId = "parity-hero"

    private fun JSONArray.records(): List<JSONObject> = List(length()) { getJSONObject(it) }
    private fun JSONObject.records(key: String) = (optJSONArray(key) ?: JSONArray()).records()
    private fun expression(record: JSONObject): String {
        val body = List(record.optInt("repeat", 1)) { record.getString("expression") }
            .joinToString(record.optString("separator", ""))
        val wrapped = "(".repeat(record.optInt("wrap", 0)) + "-".repeat(record.optInt("unary", 0)) +
            body + ")".repeat(record.optInt("wrap", 0))
        return wrapped + " ".repeat(record.optInt("padding", 0))
    }
    private fun modifiers(record: JSONObject) = record.records("modifiers").mapIndexed { index, modifier ->
        CharacterModifier("modifier-$index", heroId, modifier.getString("name"), modifier.getInt("value"))
    }
    private fun check(record: JSONObject, parse: () -> DiceExpression) {
        val name = record.getString("name")
        val result = runCatching(parse)
        assertEquals("$name: ${result.exceptionOrNull()}", record.getBoolean("valid"), result.isSuccess)
        if (result.isSuccess) {
            val parsed = result.getOrThrow()
            if (record.has("source")) assertEquals(name, record.getString("source"), parsed.source)
            if (record.has("diceCount")) assertEquals(name, record.getInt("diceCount"), parsed.diceCount)
            if (record.has("termCount")) assertEquals(name, record.getInt("termCount"), parsed.termCount)
        } else {
            assertTrue("$name must fail validation, not crash", result.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun sharedLimitsMatchTheNativeParser() {
        assertEquals(1, corpus.getInt("version"))
        val limits = corpus.getJSONObject("limits")
        assertEquals(DiceExpression.MAX_EXPRESSION_LENGTH, limits.getInt("length"))
        assertEquals(DiceExpression.MAX_NESTING_DEPTH, limits.getInt("nesting"))
        assertEquals(DiceExpression.MAX_DICE_PER_TERM, limits.getInt("dicePerTerm"))
        assertEquals(DiceExpression.MAX_TOTAL_DICE, limits.getInt("totalDice"))
        assertEquals(DiceExpression.MAX_TERMS, limits.getInt("terms"))
        assertEquals(Int.MAX_VALUE, limits.getInt("integer"))
    }

    @Test
    fun numericExpressionsMatchTheSharedCorpus() {
        corpus.records("expressions").forEach { record -> check(record) { DiceExpression.parse(expression(record)) } }
    }

    @Test
    fun resolvedTemplatesMatchTheSharedCorpus() {
        corpus.records("templates").forEach { record ->
            check(record) {
                DiceExpression.parse(RollFormulaResolver.resolveTemplate(
                    expression(record), record.optInt("level", 1), modifiers(record),
                ))
            }
        }
    }

    @Test
    fun expandedRollsMatchTheSharedCorpus() {
        corpus.records("rolls").forEach { record ->
            check(record) {
                val hero = CharacterProfile(heroId, "Parity", level = record.optInt("level", 1))
                val parts = record.records("subgroups").mapIndexed { index, part ->
                    RollSubgroup(
                        id = "part-$index", name = "Part $index", expression = expression(part),
                        operator = RollSubgroupOperator.valueOf(part.optString("operator", "ADD")),
                        includeInNormalRoll = part.optBoolean("includeInNormalRoll", true),
                    )
                }
                val rules = record.records("levelRules").mapIndexed { index, rule ->
                    RollLevelRule(
                        id = "rule-$index", kind = LevelRuleKind.valueOf(rule.getString("kind")),
                        trigger = rule.getInt("trigger"), expression = expression(rule),
                        startLevel = if (rule.has("startLevel")) rule.getInt("startLevel") else null,
                        maxRepeats = if (rule.has("maxRepeats")) rule.getInt("maxRepeats") else null,
                    )
                }
                val roll = RollDefinition("roll", heroId, "Parity", expression(record), subgroups = parts, levelRules = rules)
                DiceExpression.parse(RollFormulaResolver.resolve(hero, modifiers(record), roll).expression)
            }
        }
    }
}
