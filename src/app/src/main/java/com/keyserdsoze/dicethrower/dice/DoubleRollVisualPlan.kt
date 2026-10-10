package com.keyserdsoze.dicethrower.dice

import com.keyserdsoze.dicethrower.model.DoubleRollMode

/**
 * Pure adapter: turns the engine's already-selected numeric result into
 * scene metadata, without sampling any dice or judging the winner.
 */
data class DoubleRollVisualPlan(
    val groups: Map<Int, Int> = emptyMap(),
    val chosen: Set<Int> = emptySet(),
    val discarded: Set<Int> = emptySet(),
    val selectedGroup: Int? = null,
) {
    val isDoubleRoll: Boolean get() = selectedGroup != null
}

object DoubleRollVisualPlanner {
    fun plan(
        evaluation: DoubleRollEvaluation,
        baselineOwners: Map<Int, String>,
        extraOwners: Map<Int, String>,
        selectedPartIds: Set<String>,
    ): DoubleRollVisualPlan {
        if (evaluation.mode == DoubleRollMode.NORMAL || extraOwners.isEmpty()) return DoubleRollVisualPlan()
        val first = baselineOwners.filterValues { it in selectedPartIds }.keys
        val second = extraOwners.keys
        val groups = first.associateWith { 0 } + second.associateWith { 1 }
        // The exact set of discarded indices is owned by DoubleRollEngine.
        val discarded = evaluation.dimmedComponentIndices.intersect(groups.keys)
        val chosen = groups.keys - discarded
        val winningGroup = if (chosen.any { groups[it] == 1 }) 1 else 0
        return DoubleRollVisualPlan(groups, chosen, discarded, winningGroup)
    }
}
