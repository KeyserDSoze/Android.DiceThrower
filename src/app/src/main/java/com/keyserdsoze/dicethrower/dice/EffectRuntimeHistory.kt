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
     * Return only support-only Parts actually modified by activated Effects.
     * The initial throw never includes these Parts. Re-roll replaces visible
     * sampled components while Roll After appends them; numeric totals always
     * come from the logical snapshot, never from the renderer.
     */
    fun supportPartResults(
        execution: EffectExecutionResult?,
        supportSubgroups: List<ResolvedRollSubgroup>,
    ): List<ResolvedRollSubgroupResult> {
        if (execution == null) return emptyList()
        return supportSubgroups.mapNotNull { subgroup ->
            val numeric = execution.finalSnapshot.parts[subgroup.id] ?: return@mapNotNull null
            val samples = execution.generatedDice.filter { it.partId == subgroup.id }
            if (samples.isEmpty() && numeric.total == 0) return@mapNotNull null
            val dice = mutableListOf<DiceComponent>()
            samples.forEach { sample ->
                if (sample.kind == com.keyserdsoze.dicethrower.model.EffectActionType.REROLL) dice.clear()
                dice.addAll(sample.result.components)
            }
            val chosen = DiceRollResult(
                // Display sample components honestly; the final total can also
                // include arithmetic effects, supplied by the final snapshot.
                total = dice.sumOf { it.subtotal } + numeric.modifiers,
                components = dice,
                constantTotal = numeric.modifiers,
            )
            ResolvedRollSubgroupResult(subgroup, chosen)
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
