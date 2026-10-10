package com.keyserdsoze.dicethrower.dice

import com.keyserdsoze.dicethrower.model.EffectComparison
import com.keyserdsoze.dicethrower.model.EffectCondition
import com.keyserdsoze.dicethrower.model.EffectValueScope
import com.keyserdsoze.dicethrower.model.EffectValueSource
import com.keyserdsoze.dicethrower.model.RollEffect
import java.util.Locale

/**
 * Immutable input produced by the dice engine. No renderer or physics state is read.
 *
 * A Roll-wide value is only used when explicitly requested by a ROLL condition.
 * The UI can continue to show independent Part totals instead of a misleading global sum.
 */
data class EffectRollSnapshot(
    val roll: DiceRollResult?,
    val partsById: Map<String, DiceRollResult>,
    val variables: Map<String, Int> = emptyMap(),
) {
    companion object {
        /** A saved support-only Part has no initial sample and consumes no RNG. */
        private fun emptySupportPart(): DiceRollResult = DiceRollResult(
            total = 0, components = emptyList(), constantTotal = 0,
        )
        fun fromResolvedRoll(
            formula: ResolvedRollFormula,
            result: DiceRollResult,
            variables: Map<String, Int> = emptyMap(),
        ): EffectRollSnapshot = EffectRollSnapshot(
            roll = result,
            partsById = (if (formula.subgroups.isEmpty()) {
                mapOf("single" to result)
            } else {
                formula.subgroupResults(result).associate { it.subgroup.id to it.result }
            }) + formula.supportSubgroups.associate { it.id to emptySupportPart() },
            variables = variables,
        )

        /**
         * Evaluate the chosen double-roll candidates, never the extra displayed dice.
         * The losing candidates remain available in DoubleRollEvaluation for statistics.
         */
        fun fromDoubleRoll(
            evaluation: DoubleRollEvaluation,
            variables: Map<String, Int> = emptyMap(),
            supportSubgroups: List<ResolvedRollSubgroup> = emptyList(),
        ): EffectRollSnapshot = EffectRollSnapshot(
            roll = evaluation.result,
            partsById = evaluation.parts.associate { it.subgroup.id to it.chosen } +
                supportSubgroups.associate { it.id to emptySupportPart() },
            variables = variables,
        )
    }
}

data class EffectConditionEvaluation(
    val conditionId: String,
    val actual: Int?,
    val threshold: Int?,
    val passed: Boolean,
    /** Missing inputs and invalid formulae fail closed rather than activating an effect. */
    val error: String? = null,
)

data class EffectGroupEvaluation(
    val groupId: String,
    val passed: Boolean,
    val conditions: List<EffectConditionEvaluation>,
)

data class EffectActivationEvaluation(
    val effectId: String,
    val activated: Boolean,
    val skipped: Boolean,
    val groups: List<EffectGroupEvaluation>,
)

object EffectActivationEvaluator {

    /**
     * All groups are evaluated for an explainable trace: groups are OR-ed,
     * conditions within each group are AND-ed. No RNG is involved.
     *
     * The threshold callback is the integration point for the richer formula
     * interpreter in #105, including floor/ceil/round and decimal arithmetic.
     */
    fun evaluate(
        effect: RollEffect,
        snapshot: EffectRollSnapshot,
        thresholdResolver: (String, EffectRollSnapshot) -> Int = ::resolveIntegerThreshold,
        valueResolver: ((EffectCondition, EffectRollSnapshot) -> Int)? = null,
    ): EffectActivationEvaluation {
        if (!effect.enabled) return EffectActivationEvaluation(
            effectId = effect.id,
            activated = false,
            skipped = true,
            groups = emptyList(),
        )
        val groups = effect.activationGroups.map { group ->
            val conditions = group.conditions.map { condition ->
                evaluateCondition(condition, snapshot, thresholdResolver, valueResolver)
            }
            EffectGroupEvaluation(
                groupId = group.id,
                passed = conditions.isNotEmpty() && conditions.all { it.passed },
                conditions = conditions,
            )
        }
        return EffectActivationEvaluation(
            effectId = effect.id,
            activated = groups.any { it.passed },
            skipped = false,
            groups = groups,
        )
    }

    fun evaluateCondition(
        condition: EffectCondition,
        snapshot: EffectRollSnapshot,
        thresholdResolver: (String, EffectRollSnapshot) -> Int = ::resolveIntegerThreshold,
        valueResolver: ((EffectCondition, EffectRollSnapshot) -> Int)? = null,
    ): EffectConditionEvaluation {
        val actual = runCatching {
            valueResolver?.invoke(condition, snapshot) ?: readValue(condition, snapshot)
        }
            .getOrElse {
                return EffectConditionEvaluation(condition.id, null, null, false, it.message)
            }
        val expected = runCatching { thresholdResolver(condition.threshold, snapshot) }
            .getOrElse {
                return EffectConditionEvaluation(condition.id, actual, null, false, it.message)
            }
        val passed = when (condition.comparison) {
            EffectComparison.GREATER_OR_EQUAL -> actual >= expected
            EffectComparison.LESS_OR_EQUAL -> actual <= expected
            EffectComparison.GREATER -> actual > expected
            EffectComparison.LESS -> actual < expected
            EffectComparison.EQUAL -> actual == expected
            EffectComparison.NOT_EQUAL -> actual != expected
        }
        return EffectConditionEvaluation(condition.id, actual, expected, passed)
    }

    private fun readValue(condition: EffectCondition, snapshot: EffectRollSnapshot): Int =
        when (condition.source) {
            EffectValueSource.PART -> {
                val id = requireNotNull(condition.partId) { "Missing condition Part ID" }
                val part = requireNotNull(snapshot.partsById[id]) { "Missing roll result for Part $id" }
                scoped(part, condition.scope)
            }
            EffectValueSource.ROLL -> {
                val roll = requireNotNull(snapshot.roll) { "Missing full Roll result" }
                scoped(roll, condition.scope)
            }
            EffectValueSource.VARIABLE -> {
                require(condition.scope == EffectValueScope.TOTAL) {
                    "Variables have a total value only"
                }
                val name = requireNotNull(condition.variableName) { "Missing variable name" }
                requireNotNull(lookupVariable(name, snapshot.variables)) { "Unknown variable $name" }
            }
        }

    private fun scoped(result: DiceRollResult, scope: EffectValueScope): Int = when (scope) {
        EffectValueScope.DICE_ONLY -> result.components.sumOf { it.subtotal }
        EffectValueScope.MODIFIERS_ONLY -> result.constantTotal
        EffectValueScope.TOTAL -> result.total
    }

    /** Numerical conditions use the same deterministic math as effect actions. */
    fun resolveIntegerThreshold(template: String, snapshot: EffectRollSnapshot): Int =
        EffectFormulaInterpreter.toInt(
            EffectFormulaInterpreter.evaluate(template, snapshot),
            EffectResultRounding.FLOOR,
        )

    private fun lookupVariable(name: String, variables: Map<String, Int>): Int? {
        val normalized = name.trim().lowercase(Locale.ROOT)
        return variables.entries.firstOrNull { it.key.trim().lowercase(Locale.ROOT) == normalized }?.value
    }
}
