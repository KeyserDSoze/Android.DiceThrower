package com.keyserdsoze.dicethrower.dice

import com.keyserdsoze.dicethrower.model.DoubleRollMode
import com.keyserdsoze.dicethrower.model.RollSubgroupOperator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DoubleRollEngineTest {
    private val formula = ResolvedRollFormula(
        expression = "(1d20+2)+(2d6+3)+(1d4)",
        appliedRules = emptyList(),
        subgroups = listOf(
            ResolvedRollSubgroup("attack", "Attack", RollSubgroupOperator.ADD, "1d20+2"),
            ResolvedRollSubgroup("damage", "Damage", RollSubgroupOperator.ADD, "2d6+3"),
            ResolvedRollSubgroup("extra", "Other", RollSubgroupOperator.ADD, "1d4"),
        ),
    )

    @Test
    fun bestAndWorstCompareSumOfCompleteSelectedParts() {
        for (mode in listOf(DoubleRollMode.BEST, DoubleRollMode.WORST)) {
            val rolled = DoubleRollEngine.evaluate(formula, mode, setOf("attack", "damage"), Random(21))
            assertEquals(mode, rolled.mode)
            assertEquals(3, rolled.parts.size)
            assertNotNull(rolled.parts[0].alternative)
            assertNotNull(rolled.parts[1].alternative)
            assertNull(rolled.parts[2].alternative)
            assertEquals(
                rolled.parts.take(2).sumOf { it.chosen.total },
                rolled.comparisonTotal,
            )
            assertEquals(
                rolled.parts.take(2).sumOf { it.alternative!!.total },
                rolled.alternativeComparisonTotal,
            )
            assertEquals(rolled.parts.sumOf { it.chosen.total }, rolled.result.total)
            assertEquals(
                formula.subgroups.sumOf { DiceExpression.parse(it.expression).diceShape().size } + 2,
                rolled.visualResult.components.size,
            )
            if (mode == DoubleRollMode.BEST) {
                assertTrue(rolled.comparisonTotal!! >= rolled.alternativeComparisonTotal!!)
            } else {
                assertTrue(rolled.comparisonTotal!! <= rolled.alternativeComparisonTotal!!)
            }
            assertEquals(2, rolled.dimmedComponentIndices.size)
        }
    }

    @Test
    fun normalAndEmptyParticipationRemainSingleRoll() {
        val normal = DoubleRollEngine.evaluate(formula, DoubleRollMode.NORMAL, setOf("attack"), Random(9))
        val noParticipation = DoubleRollEngine.evaluate(formula, DoubleRollMode.BEST, emptySet(), Random(9))
        assertEquals(DoubleRollMode.NORMAL, normal.mode)
        assertEquals(DoubleRollMode.NORMAL, noParticipation.mode)
        assertEquals(normal.result, noParticipation.result)
        assertEquals(normal.result, normal.visualResult)
        assertTrue(normal.parts.all { it.alternative == null })
        assertTrue(normal.dimmedComponentIndices.isEmpty())
    }

    @Test
    fun singleLegacyPartAndLevelRulesPreserveUnselectedDice() {
        val single = ResolvedRollFormula("2d6+3", emptyList())
        val doubled = DoubleRollEngine.evaluate(single, DoubleRollMode.BEST, setOf("single"), Random(7))
        assertEquals(1, doubled.parts.size)
        assertEquals(2, doubled.visualResult.components.size)

        val scaled = formula.copy(expression = formula.expression + "+1d8+5")
        val rolled = DoubleRollEngine.evaluate(scaled, DoubleRollMode.WORST, setOf("attack"), Random(5))
        assertEquals(5, rolled.visualResult.components.size)
        assertEquals(4, rolled.result.components.size)
        assertEquals(rolled.result.components.sumOf { it.subtotal } + rolled.result.constantTotal, rolled.result.total)
    }

    @Test
    fun subtractionOperatorIsUsedInComparison() {
        val subtraction = ResolvedRollFormula(
            expression = "(1d20)-(1d6)",
            appliedRules = emptyList(),
            subgroups = listOf(
                ResolvedRollSubgroup("a", "A", RollSubgroupOperator.ADD, "1d20"),
                ResolvedRollSubgroup("b", "B", RollSubgroupOperator.SUBTRACT, "1d6"),
            ),
        )
        val result = DoubleRollEngine.evaluate(subtraction, DoubleRollMode.BEST, setOf("b"), Random(3))
        assertTrue(result.parts[1].chosen.total < 0)
        assertTrue(result.parts[1].alternative!!.total < 0)
        assertEquals(result.parts.sumOf { it.chosen.total }, result.result.total)
    }
}
