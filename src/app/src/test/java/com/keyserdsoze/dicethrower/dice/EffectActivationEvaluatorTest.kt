package com.keyserdsoze.dicethrower.dice

import com.keyserdsoze.dicethrower.model.EffectActivationGroup
import com.keyserdsoze.dicethrower.model.EffectComparison
import com.keyserdsoze.dicethrower.model.EffectCondition
import com.keyserdsoze.dicethrower.model.EffectType
import com.keyserdsoze.dicethrower.model.EffectValueScope
import com.keyserdsoze.dicethrower.model.EffectValueSource
import com.keyserdsoze.dicethrower.model.RollEffect
import com.keyserdsoze.dicethrower.model.RollSubgroupOperator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EffectActivationEvaluatorTest {
    private fun result(die: Int, modifier: Int) = DiceRollResult(
        total = die + modifier,
        components = listOf(DiceComponent(1, 20, 1, listOf(die))),
        constantTotal = modifier,
    )
    private val snapshot = EffectRollSnapshot(
        roll = result(18, 2),
        partsById = mapOf("attack" to result(18, 2)),
        variables = mapOf("level" to 12, "Strength" to 2),
    )

    private fun condition(
        id: String,
        scope: EffectValueScope = EffectValueScope.DICE_ONLY,
        comparison: EffectComparison = EffectComparison.GREATER_OR_EQUAL,
        threshold: String = "20",
    ) = EffectCondition(
        id = id, source = EffectValueSource.PART, partId = "attack",
        scope = scope, comparison = comparison, threshold = threshold,
    )

    private fun effect(vararg groups: EffectActivationGroup) = RollEffect(
        id = "critical", name = "High roll", type = EffectType.BONUS,
        activationGroups = groups.toList(),
    )

    @Test
    fun naturalDie18PlusTwoDoesNotMeetDiceOnly20ButMeetsTotal20() {
        val die = EffectActivationEvaluator.evaluateCondition(condition("die"), snapshot)
        val total = EffectActivationEvaluator.evaluateCondition(
            condition("total", EffectValueScope.TOTAL), snapshot,
        )
        val modifiers = EffectActivationEvaluator.evaluateCondition(
            condition("mods", EffectValueScope.MODIFIERS_ONLY,
                EffectComparison.EQUAL, "2"), snapshot,
        )
        assertEquals(18, die.actual)
        assertFalse(die.passed)
        assertEquals(20, total.actual)
        assertTrue(total.passed)
        assertEquals(2, modifiers.actual)
        assertTrue(modifiers.passed)
    }

    @Test
    fun groupOrAndRangeDoesNotConflateDifferentScopes() {
        val or = effect(
            EffectActivationGroup("high", listOf(condition("high", threshold = "20"))),
            EffectActivationGroup("low", listOf(condition(
                "low", comparison = EffectComparison.LESS_OR_EQUAL, threshold = "3",
            ))),
            EffectActivationGroup("range", listOf(
                condition("atLeast10", threshold = "10"),
                condition("atMost17", comparison = EffectComparison.LESS_OR_EQUAL, threshold = "17"),
            )),
        )
        val outcome = EffectActivationEvaluator.evaluate(or, snapshot)
        assertFalse(outcome.activated)
        assertEquals(listOf(false, false, false), outcome.groups.map { it.passed })
        assertEquals(listOf(1, 1, 2), outcome.groups.map { it.conditions.size })

        val withinRange = effect(
            EffectActivationGroup("range", listOf(
                condition("min", threshold = "10"),
                condition("max", comparison = EffectComparison.LESS_OR_EQUAL, threshold = "18"),
            )),
            EffectActivationGroup("other", listOf(condition("high", threshold = "20"))),
        )
        assertTrue(EffectActivationEvaluator.evaluate(withinRange, snapshot).activated)
    }

    @Test
    fun allComparisonOperatorsAreExplicit() {
        val checks = mapOf(
            EffectComparison.GREATER to (18 > 17),
            EffectComparison.LESS to (18 < 17),
            EffectComparison.GREATER_OR_EQUAL to (18 >= 18),
            EffectComparison.LESS_OR_EQUAL to (18 <= 18),
            EffectComparison.EQUAL to (18 == 18),
            EffectComparison.NOT_EQUAL to (18 != 18),
        )
        checks.forEach { (op, expected) ->
            val threshold = when (op) {
                EffectComparison.GREATER, EffectComparison.LESS -> "17"
                else -> "18"
            }
            assertEquals(op.name, expected,
                EffectActivationEvaluator.evaluateCondition(condition("compare", comparison = op,
                    threshold = threshold), snapshot).passed)
        }
    }

    @Test
    fun variableAndRollSourcesAndNamedThresholdsWork() {
        val variable = EffectCondition(
            "level", source = EffectValueSource.VARIABLE, variableName = "LEVEL",
            scope = EffectValueScope.TOTAL, threshold = "10+{Strength}",
        )
        val roll = EffectCondition(
            "roll", source = EffectValueSource.ROLL,
            scope = EffectValueScope.TOTAL, comparison = EffectComparison.EQUAL,
            threshold = "{partId:attack}",
        )
        val activated = EffectActivationEvaluator.evaluate(
            effect(EffectActivationGroup("all", listOf(variable, roll))), snapshot,
        )
        assertTrue(activated.activated)
        assertEquals(12, activated.groups.single().conditions.first().threshold)
        assertEquals(20, activated.groups.single().conditions.last().threshold)
    }

    @Test
    fun missingPartsInvalidThresholdsAndDiceThresholdsFailClosed() {
        val group = EffectActivationGroup("invalid", listOf(
            condition("missing", threshold = "{partId:deleted}"),
            condition("rngForbidden", threshold = "1d20"),
            condition("unsupported", threshold = "bad(20/3)"),
            condition("missingValue").copy(partId = "not-found"),
        ))
        val evaluation = EffectActivationEvaluator.evaluate(effect(group), snapshot)
        assertFalse(evaluation.activated)
        assertTrue(evaluation.groups.single().conditions.all { !it.passed && it.error != null })
        assertEquals(18, evaluation.groups.single().conditions[0].actual)
        assertEquals(null, evaluation.groups.single().conditions[3].actual)
        assertEquals(
            evaluation,
            EffectActivationEvaluator.evaluate(effect(group), snapshot),
        )
    }

    @Test
    fun disabledAndEmptyGroupsNeverTrigger() {
        val active = effect(EffectActivationGroup("yes", listOf(condition("gte", threshold = "18"))))
        assertTrue(EffectActivationEvaluator.evaluate(active, snapshot).activated)
        val disabled = EffectActivationEvaluator.evaluate(active.copy(enabled = false), snapshot)
        assertFalse(disabled.activated)
        assertTrue(disabled.skipped)
        assertFalse(EffectActivationEvaluator.evaluate(effect(EffectActivationGroup("empty")), snapshot).activated)
        assertFalse(EffectActivationEvaluator.evaluate(effect(), snapshot).activated)
    }

    @Test
    fun doubleRollSnapshotUsesChosenDiceNotDisplayCandidates() {
        val formula = ResolvedRollFormula(
            expression = "(1d20+2)",
            appliedRules = emptyList(),
            subgroups = listOf(ResolvedRollSubgroup(
                "attack", "Attack", RollSubgroupOperator.ADD, "1d20+2",
            )),
        )
        val rolled = DoubleRollEngine.evaluate(
            formula, com.keyserdsoze.dicethrower.model.DoubleRollMode.BEST,
            setOf("attack"), Random(12),
        )
        val chosen = EffectRollSnapshot.fromDoubleRoll(rolled)
        assertEquals(rolled.parts.single().chosen.total, chosen.partsById.getValue("attack").total)
        assertEquals(rolled.result.total, chosen.roll?.total)
        assertEquals(2, rolled.visualResult.components.size)
        assertEquals(1, chosen.partsById.getValue("attack").components.size)
    }
}
