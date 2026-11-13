package com.keyserdsoze.dicethrower.dice

import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.EffectAction
import com.keyserdsoze.dicethrower.model.EffectActionType
import com.keyserdsoze.dicethrower.model.EffectActivationGroup
import com.keyserdsoze.dicethrower.model.EffectComparison
import com.keyserdsoze.dicethrower.model.EffectCondition
import com.keyserdsoze.dicethrower.model.EffectType
import com.keyserdsoze.dicethrower.model.EffectValueScope
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollEffect
import com.keyserdsoze.dicethrower.model.RollSubgroup
import com.keyserdsoze.dicethrower.model.RollSubgroupOperator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SubtractingPartEffectTest {
    private val hero = CharacterProfile(id = "hero", name = "Hero")
    private val parts = listOf(
        RollSubgroup("attack", "Attack", "1d20+2"),
        RollSubgroup("penalty", "Penalty", "1d6+1", RollSubgroupOperator.SUBTRACT),
    )
    private val roll = RollDefinition(
        id = "contest", characterId = hero.id, name = "Contest",
        expression = RollFormulaResolver.canonicalExpression(parts),
        subgroups = parts,
    )

    @Test
    fun defaultEffectFormulaOfNormalSubtractingPartKeepsItsSign() {
        val formula = RollFormulaResolver.resolve(hero, emptyList(), roll)
        val expressions = formula.effectPartExpressions()

        assertEquals("1d20+2", expressions.getValue("attack"))
        assertEquals("-(1d6+1)", expressions.getValue("penalty"))
        assertEquals(-1, DiceExpression.parse(expressions.getValue("penalty")).diceShape().single().sign)
    }

    @Test
    fun rerollAndRollAfterOfSubtractingPartNeverTurnThePenaltyIntoABonus() {
        val formula = RollFormulaResolver.resolve(hero, emptyList(), roll)
        val original = EffectRollSnapshot.fromResolvedRoll(
            formula, DiceExpression.parse(formula.expression).evaluate(Random(3)),
        )
        val initialPenalty = original.partsById.getValue("penalty")
        assertTrue(initialPenalty.total < 0)
        val always = EffectCondition(
            "always", partId = "attack",
            comparison = EffectComparison.GREATER_OR_EQUAL, threshold = "1",
        )

        listOf(
            EffectActionType.REROLL to EffectValueScope.TOTAL,
            EffectActionType.REROLL to EffectValueScope.DICE_ONLY,
            EffectActionType.ROLL_AFTER to EffectValueScope.DICE_ONLY,
            EffectActionType.ROLL_AFTER to EffectValueScope.TOTAL,
        ).forEach { (kind, scope) ->
            val effect = RollEffect(
                id = "malus", name = "Malus", type = EffectType.MALUS,
                activationGroups = listOf(EffectActivationGroup("group", listOf(always))),
                actions = listOf(EffectAction("action", kind, "penalty", scope, expression = "")),
            )
            repeat(20) { seed ->
                val output = EffectSequenceExecutor.execute(
                    original, listOf(effect), formula.effectPartExpressions(), Random(seed),
                )
                val action = output.steps.single().actions.single()
                assertTrue("$kind/$scope was not applied: ${action.error}", action.applied)
                val sample = output.generatedDice.single().result
                assertTrue("$kind/$scope sampled positive dice", sample.components.all { it.sign < 0 })
                val penalty = output.finalSnapshot.parts.getValue("penalty")
                assertTrue("$kind/$scope turned the penalty into a bonus", penalty.dice < 0)
                assertTrue("$kind/$scope turned the penalty into a bonus", penalty.total < 0)
                if (kind == EffectActionType.ROLL_AFTER) {
                    assertTrue(penalty.total < initialPenalty.total)
                }
            }
        }
    }
}
