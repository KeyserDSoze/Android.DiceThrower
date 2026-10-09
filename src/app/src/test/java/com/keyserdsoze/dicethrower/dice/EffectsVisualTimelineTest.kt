package com.keyserdsoze.dicethrower.dice

import com.keyserdsoze.dicethrower.model.EffectActionType
import com.keyserdsoze.dicethrower.model.EffectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectsVisualTimelineTest {
    private val base = DiceRollResult(
        25,
        listOf(
            DiceComponent(1, 20, 1, listOf(20)),
            DiceComponent(1, 6, 1, listOf(5)),
        ),
        0,
    )
    private val attack = DiceRollResult(20, listOf(base.components[0]), 0)
    private val damage = DiceRollResult(5, listOf(base.components[1]), 0)
    private val original = EffectRollSnapshot(base, mapOf("attack" to attack, "damage" to damage))
    private val snapshot = EffectNumericSnapshot.fromRoll(original)
    private val followUp = EffectGeneratedDice("bonus", "extra", "damage",
        EffectActionType.ROLL_AFTER,
        DiceRollResult(3, listOf(DiceComponent(1, 4, 1, listOf(3))), 0))
    private val malusReroll = EffectGeneratedDice("malus", "reroll", "attack",
        EffectActionType.REROLL,
        DiceRollResult(7, listOf(DiceComponent(1, 20, 1, listOf(7))), 0))

    private fun execution(vararg generated: EffectGeneratedDice) = EffectExecutionResult(
        original = original,
        finalSnapshot = snapshot,
        steps = emptyList(),
        generatedDice = generated.toList(),
    )

    @Test
    fun generatedFacesAreAppendedWithoutResamplingOrChangingOriginals() {
        val samples = execution(followUp, malusReroll)
        val stages = EffectsVisualTimeline.build(
            baseline = base,
            baselineOwners = mapOf(0 to "attack", 1 to "damage"),
            execution = samples,
            effectTypes = mapOf("bonus" to EffectType.BONUS, "malus" to EffectType.MALUS),
            animate = true,
        )
        assertEquals(3, stages.size)
        assertEquals(0, stages[0].persistentDiceCount)
        assertEquals(2, stages[1].persistentDiceCount)
        assertEquals(3, stages[2].persistentDiceCount)
        assertEquals(listOf(20, 5, 3, 7), stages.last().result.components.map { it.rolls.single() })
        assertEquals(listOf(20, 5), base.components.map { it.rolls.single() })
        assertEquals(EffectType.BONUS, stages[1].accentByComponentIndex[2])
        assertEquals(EffectType.MALUS, stages[2].accentByComponentIndex[3])
        assertEquals("damage", stages[1].componentOwners[2])
        assertEquals("attack", stages[2].componentOwners[3])
    }

    @Test
    fun disabledAnimationsShowAllResolvedDiceImmediately() {
        val stages = EffectsVisualTimeline.build(base, emptyMap(), execution(followUp),
            mapOf("bonus" to EffectType.BONUS), animate = false)
        assertEquals(1, stages.size)
        assertEquals(0, stages.single().persistentDiceCount)
        assertEquals(listOf(20, 5, 3), stages.single().result.components.map { it.rolls.single() })
        assertEquals(EffectType.BONUS, stages.single().accentByComponentIndex[2])
    }

    @Test
    fun noAdditionalDiceKeepsOriginalEventUnchanged() {
        val without = EffectsVisualTimeline.build(base, mapOf(0 to "attack"),
            execution(), emptyMap(), animate = true)
        assertEquals(1, without.size)
        assertEquals(base, without.single().result)
        assertTrue(without.single().accentByComponentIndex.isEmpty())
        assertTrue(without.single().effectId == null)
    }

    @Test
    fun tableCapacityFallsBackToNewDiceRatherThanHidingExtraRoll() {
        val full = DiceRollResult(
            12, listOf(DiceComponent(12, 6, 1, List(12) { 1 })), 0,
        )
        val stages = EffectsVisualTimeline.build(full, emptyMap(), execution(followUp),
            mapOf("bonus" to EffectType.BONUS), animate = true)
        assertEquals(2, stages.size)
        assertEquals(0, stages.last().persistentDiceCount)
        assertEquals(listOf(3), stages.last().result.components.single().rolls)
        assertEquals(EffectType.BONUS, stages.last().accentByComponentIndex[0])
    }

    @Test
    fun visualStageLimitPreventsUnboundedRendererChanges() {
        val samples = Array(40) { i -> followUp.copy(actionId = "extra-$i") }
        val stages = EffectsVisualTimeline.build(base, emptyMap(), execution(*samples),
            mapOf("bonus" to EffectType.BONUS), animate = true)
        assertEquals(EffectsVisualTimeline.MAX_STAGES, stages.size)
        assertFalse(stages.last().result.components.isEmpty())
    }
}
