package com.keyserdsoze.dicethrower.dice

import com.keyserdsoze.dicethrower.model.EffectType

/**
 * Visual-only snapshots made from dice already sampled by the logical engine.
 * No calls to Random or DiceExpression.evaluate are permitted in this adapter.
 */
data class EffectsVisualStage(
    val result: DiceRollResult,
    val componentOwners: Map<Int, String>,
    val accentByComponentIndex: Map<Int, EffectType> = emptyMap(),
    /** The previous dice remain on the table; only the additional dice animate. */
    val persistentDiceCount: Int = 0,
    val effectId: String? = null,
    val rerolledComponentIndices: Set<Int> = emptySet(),
    val retainsBaseline: Boolean = true,
)

object EffectsVisualTimeline {
    const val MAX_VISIBLE_DICE = 12
    const val MAX_STAGES = 12

    fun build(
        baseline: DiceRollResult,
        baselineOwners: Map<Int, String>,
        execution: EffectExecutionResult?,
        effectTypes: Map<String, EffectType>,
        animate: Boolean,
    ): List<EffectsVisualStage> {
        val original = EffectsVisualStage(baseline, baselineOwners)
        if (execution == null || execution.generatedDice.isEmpty()) return listOf(original)

        var components = baseline.components
        var owners = baselineOwners
        var accents = emptyMap<Int, EffectType>()
        var replacedComponents = emptySet<Int>()
        var retainsBaseline = true
        val stages = mutableListOf(original)
        var displayedDice = baseline.components.sumOf { it.rolls.size }
        for (sample in execution.generatedDice.take(MAX_STAGES - 1)) {
            val added = sample.result.components
            if (added.isEmpty()) continue
            val addedCount = added.sumOf { it.rolls.size }
            val overflow = displayedDice + addedCount > MAX_VISIBLE_DICE
            // Rendering is bounded. When the table fills, show the newly generated
            // dice in a fresh phase rather than silently dropping them.
            val previousCount = if (overflow) 0 else displayedDice
            val offset = if (overflow) 0 else components.size
            if (overflow) {
                replacedComponents = emptySet()
                retainsBaseline = false
            }
            if (!overflow && sample.kind == com.keyserdsoze.dicethrower.model.EffectActionType.REROLL) {
                replacedComponents = replacedComponents + owners.filterValues { it == sample.partId }.keys
            }
            components = if (overflow) added else components + added
            owners = if (overflow) emptyMap() else owners
            owners = owners + added.indices.associate { index -> offset + index to sample.partId }
            accents = if (overflow) emptyMap() else accents
            val accent = effectTypes[sample.effectId]
            if (accent != null) {
                accents = accents + added.indices.associate { offset + it to accent }
            }
            displayedDice = components.sumOf { it.rolls.size }
            stages += EffectsVisualStage(
                result = DiceRollResult(
                    total = components.sumOf { it.subtotal },
                    components = components,
                    constantTotal = 0,
                ),
                componentOwners = owners,
                accentByComponentIndex = accents,
                persistentDiceCount = previousCount,
                effectId = sample.effectId,
                rerolledComponentIndices = replacedComponents,
                retainsBaseline = retainsBaseline,
            )
        }
        // With animations disabled, render the last already-resolved sample
        // immediately. No additional throw, delay, or physics dependency.
        return if (animate) stages else listOf(stages.last().copy(persistentDiceCount = 0))
    }
}
