package com.keyserdsoze.dicethrower.dice

import com.keyserdsoze.dicethrower.model.RollEffect
import com.keyserdsoze.dicethrower.model.RollLogEffectAction
import com.keyserdsoze.dicethrower.model.RollLogEffectCondition
import com.keyserdsoze.dicethrower.model.RollLogEffectStep

/**
 * Pure bridge from the logical dice executor to durable, renderer-independent
 * history. Retains the effect's name and type as they were at throw time.
 */
object EffectRuntimeHistory {
    fun steps(
        execution: EffectExecutionResult,
        definitions: List<RollEffect>,
    ): List<RollLogEffectStep> {
        val effectsById = definitions.associateBy { it.id }
        val samples = execution.generatedDice.associateBy { it.effectId to it.actionId }
        return execution.steps.mapNotNull { step ->
            val effect = effectsById[step.effectId] ?: return@mapNotNull null
            RollLogEffectStep(
                effectId = effect.id,
                name = effect.name,
                type = effect.type,
                activated = step.activation.activated,
                conditions = step.activation.groups.flatMap { group ->
                    group.conditions.mapIndexed { index, condition ->
                        val definition = effect.activationGroups.firstOrNull { it.id == group.groupId }
                            ?.conditions?.getOrNull(index)
                        RollLogEffectCondition(
                            groupId = group.groupId,
                            conditionId = condition.conditionId,
                            actual = condition.actual,
                            threshold = condition.threshold,
                            comparison = definition?.comparison
                                ?: com.keyserdsoze.dicethrower.model.EffectComparison.EQUAL,
                            passed = condition.passed,
                            error = condition.error,
                        )
                    }
                },
                actions = step.actions.map { action ->
                    val definition = effect.actions.firstOrNull { it.id == action.actionId }
                    val sampled = samples[effect.id to action.actionId]
                    RollLogEffectAction(
                        actionId = action.actionId,
                        kind = definition?.kind
                            ?: com.keyserdsoze.dicethrower.model.EffectActionType.ADD,
                        targetPartId = action.targetPartId,
                        scope = action.scope,
                        before = action.before,
                        after = action.after,
                        applied = action.applied,
                        generatedDiceDetail = sampled?.result?.detail(),
                        error = action.error,
                    )
                },
            )
        }
    }

    /**
     * Preserve the legacy single scalar for old consumers, but change it only
     * by the sum of actual Part deltas. Unrelated Parts remain distinct in UI.
     */
    fun adjustedLegacyTotal(execution: EffectExecutionResult, originalTotal: Int): Int {
        val delta = execution.finalSnapshot.parts.entries.sumOf { (id, value) ->
            value.total.toLong() -
                (execution.original.partsById[id]?.total?.toLong() ?: value.total.toLong())
        }
        val candidate = originalTotal.toLong() + delta
        require(candidate in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            "Effect result is outside supported integer range"
        }
        return candidate.toInt()
    }
}
