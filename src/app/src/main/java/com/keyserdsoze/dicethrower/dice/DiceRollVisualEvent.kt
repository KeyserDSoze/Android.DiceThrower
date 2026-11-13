package com.keyserdsoze.dicethrower.dice

import com.keyserdsoze.dicethrower.model.EffectType
import com.keyserdsoze.dicethrower.model.RollVisualEffectsSettings

data class DiceRollVisualEvent(
    val id: Long,
    val result: DiceRollResult,
    val appearances: List<ResolvedDiceAppearance>,
    val dimmedComponentIndices: Set<Int> = emptySet(),
    val effectAccentComponents: Map<Int, EffectType> = emptyMap(),
    val persistentDiceCount: Int = 0,
    /** 0 = original candidate (upper lane), 1 = alternative (lower lane). */
    val candidateGroupByComponent: Map<Int, Int> = emptyMap(),
    /** Chosen candidate component indices; the engine decides this before animation. */
    val chosenCandidateComponents: Set<Int> = emptySet(),
    val visualSettings: RollVisualEffectsSettings = RollVisualEffectsSettings(),
)
