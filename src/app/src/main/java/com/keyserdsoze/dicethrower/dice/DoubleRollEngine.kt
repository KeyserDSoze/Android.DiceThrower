package com.keyserdsoze.dicethrower.dice

import com.keyserdsoze.dicethrower.model.DoubleRollMode
import com.keyserdsoze.dicethrower.model.RollSubgroupOperator
import kotlin.random.Random

/**
 * Numerical double-roll evaluation. The renderer receives all sampled dice, but cannot
 * choose a winner: the engine selects a *whole candidate group* by the sum of its
 * participating Part totals. Unselected Parts are sampled exactly once.
 */
data class DoubleRollPartOutcome(
    val subgroup: ResolvedRollSubgroup,
    val chosen: DiceRollResult,
    val alternative: DiceRollResult? = null,
)

data class DoubleRollEvaluation(
    val mode: DoubleRollMode,
    val result: DiceRollResult,
    val visualResult: DiceRollResult,
    val parts: List<DoubleRollPartOutcome>,
    val comparisonTotal: Int? = null,
    val alternativeComparisonTotal: Int? = null,
    /** Component indices in visualResult, not in the selected numerical result. */
    val dimmedComponentIndices: Set<Int> = emptySet(),
)

object DoubleRollEngine {
    fun evaluate(
        formula: ResolvedRollFormula,
        mode: DoubleRollMode,
        includedPartIds: Set<String>,
        random: Random = Random.Default,
    ): DoubleRollEvaluation {
        val first = DiceExpression.parse(formula.expression).evaluate(random)
        val parts = if (formula.subgroups.isNotEmpty()) {
            formula.subgroupResults(first).map {
                DoubleRollPartOutcome(it.subgroup, it.result)
            }
        } else {
            listOf(
                DoubleRollPartOutcome(
                    ResolvedRollSubgroup(
                        id = "single",
                        name = "",
                        operator = RollSubgroupOperator.ADD,
                        expression = formula.expression,
                    ),
                    first,
                ),
            )
        }
        val selectedIndices = parts.indices.filter { parts[it].subgroup.id in includedPartIds }
        if (mode == DoubleRollMode.NORMAL || selectedIndices.isEmpty()) {
            return DoubleRollEvaluation(DoubleRollMode.NORMAL, first, first, parts)
        }

        val secondByIndex = selectedIndices.associateWith { index ->
            val part = parts[index].subgroup
            val sampled = DiceExpression.parse(part.expression).evaluate(random)
            if (part.operator == RollSubgroupOperator.SUBTRACT) {
                sampled.copy(
                    total = -sampled.total,
                    constantTotal = -sampled.constantTotal,
                    components = sampled.components.map { it.copy(sign = -it.sign) },
                )
            } else sampled
        }
        val firstSum = selectedIndices.sumOf { parts[it].chosen.total }
        val secondSum = selectedIndices.sumOf { secondByIndex.getValue(it).total }
        val selectSecond = when (mode) {
            DoubleRollMode.BEST -> secondSum > firstSum
            DoubleRollMode.WORST -> secondSum < firstSum
            DoubleRollMode.NORMAL -> false
        }

        val chosenParts = parts.mapIndexed { index, part ->
            val other = secondByIndex[index]
            if (other == null) part else part.copy(
                chosen = if (selectSecond) other else part.chosen,
                alternative = if (selectSecond) part.chosen else other,
            )
        }

        // Level rules are appended to the canonical Part expression. Preserve those
        // sampled dice and constants once, even though they are not double-rolled.
        val partComponentCount = parts.sumOf { it.chosen.components.size }
        val unchangedTail = first.components.drop(partComponentCount)
        val selectedComponents = chosenParts.flatMap { it.chosen.components } + unchangedTail
        val selectedConstant = first.constantTotal +
            chosenParts.indices.sumOf { chosenParts[it].chosen.constantTotal - parts[it].chosen.constantTotal }
        val selectedResult = DiceRollResult(
            total = selectedComponents.sumOf { it.subtotal } + selectedConstant,
            components = selectedComponents,
            constantTotal = selectedConstant,
        )

        val extras = selectedIndices.flatMap { secondByIndex.getValue(it).components }
        val dimmed = if (!selectSecond) {
            (first.components.size until first.components.size + extras.size).toSet()
        } else {
            val selectedSet = selectedIndices.toSet()
            buildSet {
                var start = 0
                parts.forEachIndexed { index, part ->
                    if (index in selectedSet) {
                        addAll(start until start + part.chosen.components.size)
                    }
                    start += part.chosen.components.size
                }
            }
        }
        return DoubleRollEvaluation(
            mode = mode,
            result = selectedResult,
            visualResult = DiceRollResult(
                total = first.total + extras.sumOf { it.subtotal },
                components = first.components + extras,
                constantTotal = first.constantTotal,
            ),
            parts = chosenParts,
            comparisonTotal = if (selectSecond) secondSum else firstSum,
            alternativeComparisonTotal = if (selectSecond) firstSum else secondSum,
            dimmedComponentIndices = dimmed,
        )
    }
}
