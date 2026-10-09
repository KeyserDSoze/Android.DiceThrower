package com.keyserdsoze.dicethrower.dice

import com.keyserdsoze.dicethrower.model.EffectAction
import com.keyserdsoze.dicethrower.model.EffectActionType
import com.keyserdsoze.dicethrower.model.EffectActivationGroup
import com.keyserdsoze.dicethrower.model.EffectCondition
import com.keyserdsoze.dicethrower.model.EffectType
import com.keyserdsoze.dicethrower.model.EffectValueScope
import com.keyserdsoze.dicethrower.model.RollEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EffectSequenceExecutorTest {
    private fun result(value: Int, modifier: Int = 0, sides: Int = 20): DiceRollResult =
        DiceRollResult(
            total = value + modifier,
            components = listOf(DiceComponent(1, sides, 1, listOf(value))),
            constantTotal = modifier,
        )

    private val initial = EffectRollSnapshot(
        roll = DiceRollResult(24, listOf(
            DiceComponent(1, 20, 1, listOf(20)),
            DiceComponent(1, 6, 1, listOf(4)),
        ), 0),
        partsById = mapOf("attack" to result(20), "damage" to result(4, sides = 6)),
        variables = mapOf("level" to 20),
    )
    private val expressions = mapOf("attack" to "1d20", "damage" to "1d6")

    private fun effect(
        id: String,
        order: Int,
        sourcePart: String,
        threshold: String,
        action: EffectAction,
        stop: Boolean = false,
    ) = RollEffect(
        id = id, name = id, type = EffectType.BONUS, order = order,
        stopFollowingEffects = stop,
        activationGroups = listOf(EffectActivationGroup("group-$id", listOf(
            EffectCondition("when-$id", partId = sourcePart, threshold = threshold),
        ))),
        actions = listOf(action),
    )

    @Test
    fun priorityAndStopFollowingPreventLowerPriorityEffect() {
        val strong = effect("strong", 0, "attack", "20",
            EffectAction("x2", EffectActionType.MULTIPLY, "damage",
                EffectValueScope.TOTAL, "2"), stop = true)
        val weak = effect("weak", 1, "attack", "16",
            EffectAction("add5", EffectActionType.ADD, "damage",
                EffectValueScope.TOTAL, "5"))
        val result = EffectSequenceExecutor.execute(initial, listOf(weak, strong), expressions)
        assertEquals(8, result.finalSnapshot.parts.getValue("damage").total)
        assertEquals("strong", result.stoppedByEffectId)
        assertEquals(listOf("strong"), result.steps.map { it.effectId })
        assertEquals(4, result.steps.single().actions.single().before)
        assertEquals(8, result.steps.single().actions.single().after)
        assertFalse(result.executionLimitReached)
    }

    @Test
    fun noExclusivityAppliesBothActionsInConfiguredOrder() {
        val first = effect("times2", 0, "attack", "20",
            EffectAction("x2", EffectActionType.MULTIPLY, "damage",
                EffectValueScope.TOTAL, "2"))
        val second = effect("plus5", 1, "attack", "16",
            EffectAction("add5", EffectActionType.ADD, "damage",
                EffectValueScope.TOTAL, "5"))
        val result = EffectSequenceExecutor.execute(initial, listOf(second, first), expressions)
        assertEquals(13, result.finalSnapshot.parts.getValue("damage").total)
        assertEquals(listOf("times2", "plus5"), result.steps.map { it.effectId })
        assertEquals(null, result.stoppedByEffectId)
        assertTrue(result.generatedDice.isEmpty())
    }

    @Test
    fun rollAfterAddsNewDiceWithTraceAndCanTriggerFollowingEffect() {
        val generate = effect("generate", 0, "damage", "4",
            EffectAction("roll-after", EffectActionType.ROLL_AFTER,
                "attack", EffectValueScope.DICE_ONLY, "1d20"))
        val bonus = effect("bonus", 1, "attack", "21",
            EffectAction("plus", EffectActionType.ADD,
                "damage", EffectValueScope.TOTAL, "3"))
        val outcome = EffectSequenceExecutor.execute(
            initial, listOf(bonus, generate), expressions, Random(2),
        )
        assertEquals(1, outcome.generatedDice.size)
        assertEquals(EffectActionType.ROLL_AFTER, outcome.generatedDice.single().kind)
        assertTrue(outcome.finalSnapshot.parts.getValue("attack").dice > 20)
        assertEquals(7, outcome.finalSnapshot.parts.getValue("damage").total)
        assertEquals(listOf("generate", "bonus"), outcome.steps.map { it.effectId })
        assertFalse(outcome.executionLimitReached)
        assertEquals(20, initial.partsById.getValue("attack").total)
    }

    @Test
    fun rerollReplacesSelectedDiceAndPreservesUnchangedPart() {
        val effect = effect("reroll", 0, "attack", "20",
            EffectAction("reroll-attack", EffectActionType.REROLL,
                "attack", EffectValueScope.DICE_ONLY))
        val outcome = EffectSequenceExecutor.execute(initial, listOf(effect), expressions, Random(9))
        assertEquals(1, outcome.generatedDice.size)
        val sampled = outcome.generatedDice.single().result
        assertEquals(sampled.components.sumOf { it.subtotal },
            outcome.finalSnapshot.parts.getValue("attack").dice)
        assertEquals(4, outcome.finalSnapshot.parts.getValue("damage").total)
        assertTrue(outcome.steps.single().actions.single().applied)
    }

    @Test
    fun selfTriggeringRollAfterCanOnlyExecuteOnce() {
        val loop = effect("loop", 0, "attack", "1",
            EffectAction("again", EffectActionType.ROLL_AFTER,
                "attack", EffectValueScope.DICE_ONLY, "1d20"))
        val outcome = EffectSequenceExecutor.execute(initial, listOf(loop), expressions, Random(1))
        assertEquals(1, outcome.generatedDice.size)
        assertEquals(1, outcome.steps.count { it.effectId == "loop" })
        assertFalse(outcome.executionLimitReached)
    }

    @Test
    fun missingTargetAndNonsensicalModifiersOnlyDiceRerollFailClosed() {
        val missing = effect("missing", 0, "attack", "1",
            EffectAction("broken", EffectActionType.REROLL,
                "not-found", EffectValueScope.TOTAL))
        val invalidScope = effect("invalid", 1, "attack", "1",
            EffectAction("bad", EffectActionType.REROLL,
                "damage", EffectValueScope.MODIFIERS_ONLY, "1d6"))
        val outcome = EffectSequenceExecutor.execute(initial,
            listOf(missing, invalidScope), expressions, Random(1))
        assertEquals(2, outcome.steps.size)
        assertTrue(outcome.steps.flatMap { it.actions }.all { !it.applied && it.error != null })
        assertTrue(outcome.generatedDice.isEmpty())
        assertEquals(4, outcome.finalSnapshot.parts.getValue("damage").total)
    }

    @Test
    fun excessiveRulesAreRejectedWithoutSamplingRandom() {
        val action = EffectAction("plus", EffectActionType.ADD, "damage",
            EffectValueScope.TOTAL, "1")
        val rules = List(65) { i -> effect("effect-$i", i, "attack", "1", action) }
        val outcome = EffectSequenceExecutor.execute(initial, rules, expressions, Random(5))
        assertTrue(outcome.executionLimitReached)
        assertTrue(outcome.steps.isEmpty())
        assertTrue(outcome.generatedDice.isEmpty())
        assertEquals(4, outcome.finalSnapshot.parts.getValue("damage").total)
    }
}
