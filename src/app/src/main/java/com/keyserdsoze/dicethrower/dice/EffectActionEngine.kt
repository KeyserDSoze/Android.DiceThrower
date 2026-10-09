package com.keyserdsoze.dicethrower.dice

import com.keyserdsoze.dicethrower.model.EffectAction
import com.keyserdsoze.dicethrower.model.EffectActionType
import com.keyserdsoze.dicethrower.model.EffectValueScope

/**
 * Mutable-in-time, immutable-in-structure numeric projection of the original
 * sampled dice. It preserves the original faces so formulas cannot rewrite RNG.
 *
 * A TOTAL-only adjustment does not alter the natural dice or modifiers scopes.
 */
data class EffectPartValue(
    val original: DiceRollResult,
    val dice: Int = original.components.sumOf { it.subtotal },
    val modifiers: Int = original.constantTotal,
    val totalAdjustment: Int = 0,
) {
    val total: Int get() = Math.addExact(Math.addExact(dice, modifiers), totalAdjustment)

    fun value(scope: EffectValueScope): Int = when (scope) {
        EffectValueScope.DICE_ONLY -> dice
        EffectValueScope.MODIFIERS_ONLY -> modifiers
        EffectValueScope.TOTAL -> total
    }

    fun withValue(scope: EffectValueScope, newValue: Int): EffectPartValue = when (scope) {
        EffectValueScope.DICE_ONLY -> copy(dice = newValue)
        EffectValueScope.MODIFIERS_ONLY -> copy(modifiers = newValue)
        EffectValueScope.TOTAL -> copy(
            totalAdjustment = Math.subtractExact(
                Math.subtractExact(newValue, dice), modifiers,
            ),
        )
    }
}

data class EffectNumericSnapshot(
    val parts: Map<String, EffectPartValue>,
    val variables: Map<String, Int> = emptyMap(),
) {
    companion object {
        fun fromRoll(snapshot: EffectRollSnapshot): EffectNumericSnapshot = EffectNumericSnapshot(
            snapshot.partsById.mapValues { EffectPartValue(it.value) },
            snapshot.variables,
        )
    }
}

data class EffectActionResult(
    val actionId: String,
    val applied: Boolean,
    val targetPartId: String?,
    val scope: EffectValueScope,
    val before: Int?,
    val after: Int?,
    val error: String? = null,
    val snapshot: EffectNumericSnapshot,
)

/** Deterministic numeric actions. Additional dice and chained rerolls are handled by #106. */
object EffectActionEngine {
    fun apply(
        action: EffectAction,
        snapshot: EffectNumericSnapshot,
        rounding: EffectResultRounding = EffectResultRounding.FLOOR,
    ): EffectActionResult {
        val id = action.targetPartId
        val original = id?.let { snapshot.parts[it] }
        val before = original?.value(action.scope)
        val updated = runCatching {
            require(id != null && original != null) { "Effect action needs an existing Part target" }
            require(action.kind in setOf(
                EffectActionType.ADD,
                EffectActionType.SUBTRACT,
                EffectActionType.MULTIPLY,
                EffectActionType.REPLACE,
            )) { "Rerolls and roll-after require the chained dice executor" }
            val operand = EffectFormulaInterpreter.evaluate(
                action.expression,
                variables = snapshot.variables.mapValues { it.value.toDouble() },
                partTotals = snapshot.parts.mapValues { it.value.total.toDouble() },
            )
            val result = when (action.kind) {
                EffectActionType.ADD -> before!!.toDouble() + operand
                EffectActionType.SUBTRACT -> before!!.toDouble() - operand
                EffectActionType.MULTIPLY -> before!!.toDouble() * operand
                EffectActionType.REPLACE -> operand
                else -> error("Unsupported action")
            }
            val updatedPart = original.withValue(
                action.scope, EffectFormulaInterpreter.toInt(result, rounding),
            )
            // Verify the entire resulting Part stays inside Int range.
            updatedPart.total
            snapshot.copy(parts = snapshot.parts + (id to updatedPart))
        }
        return updated.fold(
            onSuccess = { changed ->
                EffectActionResult(action.id, true, id, action.scope,
                    before, changed.parts.getValue(id!!).value(action.scope),
                    snapshot = changed)
            },
            onFailure = { error ->
                EffectActionResult(action.id, false, id, action.scope,
                    before, null, error.message ?: "Invalid effect action", snapshot)
            },
        )
    }
}
