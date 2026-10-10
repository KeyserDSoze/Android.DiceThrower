package com.keyserdsoze.dicethrower.dice

import com.keyserdsoze.dicethrower.model.DoubleRollMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleRollVisualPlannerTest {
    @Test
    fun legacyRollWithNoStoredPartsStillSeparatesAndRevealsBothCandidates() {
        // Matches the canonical single-Part IDs produced by RollScreenV2
        // for a RollDefinition with no named subgroup records.
        val first = DiceRollResult(12,
            listOf(DiceComponent(1, 20, 1, listOf(12))), 0)
        val alternative = DiceRollResult(17,
            listOf(DiceComponent(1, 20, 1, listOf(17))), 0)
        val visual = DiceRollResult(29,
            first.components + alternative.components, 0)
        val evaluation = DoubleRollEvaluation(
            mode = DoubleRollMode.BEST, result = alternative,
            visualResult = visual, parts = emptyList(),
            dimmedComponentIndices = setOf(0),
        )
        val plan = DoubleRollVisualPlanner.plan(
            evaluation, mapOf(0 to "single"), mapOf(1 to "single"),
            selectedPartIds = setOf("single"),
        )
        assertTrue(plan.isDoubleRoll)
        assertEquals(mapOf(0 to 0, 1 to 1), plan.groups)
        assertEquals(setOf(1), plan.chosen)
        assertEquals(setOf(0), plan.discarded)
        assertEquals(1, plan.selectedGroup)
    }

    private val first = DiceRollResult(
        25, listOf(
            DiceComponent(1, 20, 1, listOf(17)),
            DiceComponent(1, 6, 1, listOf(4)),
            DiceComponent(1, 4, 1, listOf(4)),
        ), 0,
    )
    private val second = DiceRollResult(
        23, listOf(DiceComponent(1, 20, 1, listOf(12)),
            DiceComponent(1, 6, 1, listOf(5))), 0,
    )
    private val visual = first.copy(components = first.components + second.components)

    @Test
    fun engineChosenFirstGroupIsHighlightedOnlyAfterRendererReveal() {
        val evaluation = DoubleRollEvaluation(
            DoubleRollMode.BEST, first, visual, emptyList(),
            dimmedComponentIndices = setOf(3, 4),
        )
        val plan = DoubleRollVisualPlanner.plan(evaluation,
            baselineOwners = mapOf(0 to "attack", 1 to "damage", 2 to "unrelated"),
            extraOwners = mapOf(3 to "attack", 4 to "damage"),
            selectedPartIds = setOf("attack", "damage"),
        )
        assertEquals(mapOf(0 to 0, 1 to 0, 3 to 1, 4 to 1), plan.groups)
        assertEquals(setOf(0, 1), plan.chosen)
        assertEquals(setOf(3, 4), plan.discarded)
        assertEquals(0, plan.selectedGroup)
        assertEquals(first, evaluation.result)
    }

    @Test
    fun worstCandidateCanSelectSecondAndNeverDimTheChosenGroup() {
        val evaluation = DoubleRollEvaluation(
            DoubleRollMode.WORST, second, visual, emptyList(),
            dimmedComponentIndices = setOf(0, 1),
        )
        val plan = DoubleRollVisualPlanner.plan(evaluation,
            mapOf(0 to "attack", 1 to "damage"), mapOf(3 to "attack", 4 to "damage"),
            setOf("attack", "damage"),
        )
        assertEquals(1, plan.selectedGroup)
        assertEquals(setOf(3, 4), plan.chosen)
        assertEquals(setOf(0, 1), plan.discarded)
        assertFalse(plan.chosen.any { it in plan.discarded })
    }

    @Test
    fun normalModeOrNoEligiblePartsCannotCreateFakeCandidates() {
        val normal = DoubleRollEvaluation(DoubleRollMode.NORMAL, first, first, emptyList())
        assertFalse(DoubleRollVisualPlanner.plan(normal, mapOf(0 to "attack"), emptyMap(),
            setOf("attack")).isDoubleRoll)
        assertFalse(DoubleRollVisualPlanner.plan(normal.copy(mode = DoubleRollMode.BEST),
            mapOf(0 to "attack"), emptyMap(), setOf("attack")).isDoubleRoll)
        assertTrue(normal.result.components == first.components)
    }
}
