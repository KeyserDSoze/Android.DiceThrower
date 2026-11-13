package com.keyserdsoze.dicethrower.dice

import com.keyserdsoze.dicethrower.model.EffectActionType
import com.keyserdsoze.dicethrower.model.EffectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectsVisualTimelineTest {
    @Test
    fun generatedStageRetainsActionTypeWithoutNewLogicalDiceSampling() {
        val stages = EffectsVisualTimeline.build(
            baseline = base,
            baselineOwners = emptyMap(),
            execution = execution(followUp, malusReroll),
            effectTypes = mapOf("bonus" to EffectType.BONUS, "malus" to EffectType.MALUS),
            animate = true,
        )
        assertEquals(EffectActionType.ROLL_AFTER, stages[1].actionKind)
        assertEquals(EffectActionType.REROLL, stages[2].actionKind)
        assertEquals("damage", stages[1].targetPartId)
        assertEquals("attack", stages[2].targetPartId)
        assertEquals(base, stages[0].result)
    }


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
        assertEquals(setOf(0), stages[2].rerolledComponentIndices)
        assertTrue(stages[2].retainsBaseline)
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
        assertFalse(stages.last().retainsBaseline)
        assertEquals(listOf(3), stages.last().result.components.single().rolls)
        assertEquals(EffectType.BONUS, stages.last().accentByComponentIndex[0])
    }

    @Test
    fun sixPercentilePairsLeaveNoSpaceForAnAdditionalEffectDie() {
        val full = DiceRollResult(60, listOf(DiceComponent(6, 100, 1, List(6) { 10 })), 0)
        val stages = EffectsVisualTimeline.build(full, emptyMap(), execution(followUp),
            mapOf("bonus" to EffectType.BONUS), animate = true)
        assertEquals(0, stages.last().persistentDiceCount)
        assertFalse(stages.last().retainsBaseline)
        assertEquals(followUp.result.components, stages.last().result.components)
    }

    @Test
    fun generatedPercentilePairRequiresTwoFreeSpacesRatherThanOne() {
        val almostFull = DiceRollResult(11, listOf(DiceComponent(11, 6, 1, List(11) { 1 })), 0)
        val percentile = followUp.copy(result = DiceRollResult(65, listOf(DiceComponent(1, 100, 1, listOf(65))), 0))
        val stages = EffectsVisualTimeline.build(almostFull, emptyMap(), execution(percentile),
            mapOf("bonus" to EffectType.BONUS), animate = true)
        val last = stages.last()
        assertEquals(0, last.persistentDiceCount)
        assertFalse(last.retainsBaseline)
        assertEquals(percentile.result.components, last.result.components)
        assertEquals(listOf(100, 10), DiceVisualProjection.project(last.result).faces.map { it.sides })
    }

    @Test
    fun mixedBaselineAtElevenBodiesCanKeepItsLogicalPrefixAndAddOneEffectDie() {
        val baseline = DiceRollResult(51, listOf(
            DiceComponent(5, 100, 1, List(5) { 10 }), DiceComponent(1, 6, 1, listOf(1)),
        ), 0)
        val stages = EffectsVisualTimeline.build(baseline, emptyMap(), execution(followUp),
            mapOf("bonus" to EffectType.BONUS), animate = true)
        val last = stages.last()
        assertTrue(last.retainsBaseline)
        assertEquals(6, last.persistentDiceCount)
        val projection = DiceVisualProjection.project(last.result, last.persistentDiceCount)
        assertEquals(11, projection.persistentDiceCount)
        assertEquals(12, projection.faces.size)
        assertEquals(4, projection.faces.last().sides)
    }

    @Test
    fun consecutiveEffectsRetainBothBodiesOfEarlierPercentileSamples() {
        val baseline = DiceRollResult(100, listOf(DiceComponent(1, 100, 1, listOf(100))), 0)
        val stages = EffectsVisualTimeline.build(baseline, emptyMap(), execution(followUp, malusReroll),
            mapOf("bonus" to EffectType.BONUS, "malus" to EffectType.MALUS), animate = true)
        assertEquals(listOf(0, 1, 2), stages.map { it.persistentDiceCount })
        assertEquals(listOf(0, 2, 3), stages.map {
            DiceVisualProjection.project(it.result, it.persistentDiceCount).persistentDiceCount
        })
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
