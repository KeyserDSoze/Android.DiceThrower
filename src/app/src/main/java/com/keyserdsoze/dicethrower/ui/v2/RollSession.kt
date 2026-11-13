package com.keyserdsoze.dicethrower.ui.v2

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsPropertyKey
import com.keyserdsoze.dicethrower.dice.DiceRollResult
import com.keyserdsoze.dicethrower.dice.DiceRollVisualEvent
import com.keyserdsoze.dicethrower.dice.DoubleRollEvaluation
import com.keyserdsoze.dicethrower.dice.EffectExecutionResult
import com.keyserdsoze.dicethrower.dice.EffectsVisualStage
import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.DoubleRollMode
import com.keyserdsoze.dicethrower.model.RollDefinition

/** Read-only rendering readiness for native UI tests; not an accessibility label or stored data. */
internal val RollTableRenderedKey = SemanticsPropertyKey<Boolean>("RollTableRendered")

/** Changes to any numerical input/definition invalidate the live result; history writes do not. */
internal data class RollSessionKey(
    val roll: RollDefinition,
    val characterLevel: Int,
    val modifiers: List<CharacterModifier>,
    val resolvedExpression: String,
)

/**
 * One active roll's presentation, retained by the Activity's ViewModel. Holds only immutable
 * logical results and UI state: no Activity, Bitmap, GL view, RNG or persistence callback.
 */
internal class RollSession(val key: RollSessionKey, preview: DiceRollVisualEvent) {
    val visualEvent = mutableStateOf(preview)
    val finalVisualEvent = mutableStateOf<DiceRollVisualEvent?>(null)
    val outcome = mutableStateOf<DiceRollResult?>(null)
    val doubleEvaluation = mutableStateOf<DoubleRollEvaluation?>(null)
    val effectsExecution = mutableStateOf<EffectExecutionResult?>(null)
    val finalRollTotal = mutableStateOf<Int?>(null)
    val visualStages = mutableStateOf<List<EffectsVisualStage>>(emptyList())
    val visualStageIndex = mutableStateOf(0)
    val visualSeed = mutableStateOf(0L)
    val hasRolled = mutableStateOf(false)
    val resultRevealed = mutableStateOf(false)
    val showStats = mutableStateOf(false)
    val lastThrowMode = mutableStateOf(DoubleRollMode.NORMAL)
    val restored = mutableStateOf(false)
    val lastThrowRequestAtNanos = mutableStateOf<Long?>(null)
    val cooldownReady = mutableStateOf(true)

    val controlsReady: Boolean
        get() = (!hasRolled.value || resultRevealed.value) && cooldownReady.value

    fun acceptRequest(nowNanos: Long): Boolean {
        // Read live state, not a callback's potentially stale composition snapshot.
        if (!controlsReady) return false
        cooldownReady.value = false
        lastThrowRequestAtNanos.value = nowNanos
        return true
    }

    fun completeCooldown(requestNanos: Long) {
        if (lastThrowRequestAtNanos.value == requestNanos) cooldownReady.value = true
    }

    fun acceptsSettledEvent(eventId: Long): Boolean =
        hasRolled.value && !resultRevealed.value && visualEvent.value.id == eventId

    /**
     * On recreation/editor return, finish presentation with the already-sampled final dice.
     * Do not replay physics, sample effects again, announce a new throw or write another log.
     * Exact in-flight positions are deliberately not saved as Android/GL objects.
     */
    fun detach() {
        if (!hasRolled.value) return
        finalVisualEvent.value?.let { visualEvent.value = it }
        visualStageIndex.value = visualStages.value.lastIndex.coerceAtLeast(0)
        resultRevealed.value = true
        restored.value = true
    }

    companion object {
        const val REQUEST_COOLDOWN_NANOS = 450_000_000L
    }
}
