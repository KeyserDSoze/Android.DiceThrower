package com.keyserdsoze.dicethrower.dice

import com.keyserdsoze.dicethrower.model.EffectAction
import com.keyserdsoze.dicethrower.model.EffectActionType
import com.keyserdsoze.dicethrower.model.EffectCondition
import com.keyserdsoze.dicethrower.model.EffectValueScope
import com.keyserdsoze.dicethrower.model.EffectValueSource
import com.keyserdsoze.dicethrower.model.RollEffect
import java.util.Locale
import kotlin.random.Random

data class EffectGeneratedDice(
    val effectId: String,
    val actionId: String,
    val partId: String,
    val kind: EffectActionType,
    val result: DiceRollResult,
)

data class EffectExecutionStep(
    val effectId: String,
    val activation: EffectActivationEvaluation,
    val actions: List<EffectActionResult> = emptyList(),
)

data class EffectExecutionResult(
    val original: EffectRollSnapshot,
    val final: EffectNumericSnapshot,
    val steps: List<EffectExecutionStep>,
    val generatedDice: List<EffectGeneratedDice>,
    val stoppedByEffectId: String? = null,
    val executionLimitReached: Boolean = false,
)

/**
 * Ordered effect executor with bounded chaining. Once an effect activates it can
 * never activate again in the same throw; this avoids A -> B -> A loops.
 *
 * A newly generated die may satisfy an effect previously skipped because its
 * condition was false. The executor retries the *not-yet-activated* effects for
 * at most MAX_PASSES passes. Physics and 3D rendering never decide the outcome.
 */
object EffectSequenceExecutor {
    private const val MAX_EFFECTS = 64
    private const val MAX_ACTIONS = 128
    private const val MAX_GENERATED_DICE = 100
    private const val MAX_PASSES = 8

    fun execute(
        original: EffectRollSnapshot,
        effects: List<RollEffect>,
        resolvedPartExpressions: Map<String, String>,
        random: Random = Random.Default,
    ): EffectExecutionResult {
        var numeric = EffectNumericSnapshot.fromRoll(original)
        val traces = mutableListOf<EffectExecutionStep>()
        val generated = mutableListOf<EffectGeneratedDice>()
        var stoppedBy: String? = null
        val activatedIds = mutableSetOf<String>()
        var actionsCount = 0
        var limited = false

        val ordered = effects.filter { it.enabled }.sortedWith(compareBy({ it.order }, { it.id }))
        if (ordered.size > MAX_EFFECTS) return EffectExecutionResult(
            original, numeric, emptyList(), emptyList(), executionLimitReached = true,
        )
        repeat(MAX_PASSES) { pass ->
            var generatedThisPass = false
            for (effect in ordered) {
                if (effect.id in activatedIds) continue
                val current = numeric
                val evaluation = EffectActivationEvaluator.evaluate(
                    effect,
                    original,
                    thresholdResolver = { expression, source ->
                        EffectFormulaInterpreter.toInt(
                            EffectFormulaInterpreter.evaluate(
                                expression,
                                variables = source.variables.mapValues { it.value.toDouble() },
                                partTotals = current.parts.mapValues { it.value.total.toDouble() },
                            ),
                        )
                    },
                    valueResolver = { condition, source ->
                        readCurrent(condition, source, current)
                    },
                )
                if (!evaluation.activated) {
                    // Preserve one trace per evaluated group in the final pass only.
                    if (pass == MAX_PASSES - 1) traces += EffectExecutionStep(effect.id, evaluation)
                    continue
                }
                activatedIds += effect.id
                val results = mutableListOf<EffectActionResult>()
                for (action in effect.actions) {
                    if (++actionsCount > MAX_ACTIONS) {
                        limited = true
                        break
                    }
                    when (action.kind) {
                        EffectActionType.REROLL, EffectActionType.ROLL_AFTER -> {
                            val result = applyDiceAction(
                                effect.id, action, numeric, resolvedPartExpressions, random,
                            )
                            numeric = result.first
                            results += result.second
                            result.third?.let { sampled ->
                                val additionalDice = sampled.result.components.sumOf { it.rolls.size }
                                if (generated.sumOf { it.result.components.sumOf { component -> component.rolls.size } } +
                                    additionalDice > MAX_GENERATED_DICE) {
                                    // Reject this action rather than retaining an unbounded sample.
                                    numeric = result.second.snapshot
                                    results.removeAt(results.lastIndex)
                                    results += result.second.copy(
                                        applied = false,
                                        after = null,
                                        error = "Effect generated dice limit exceeded",
                                        snapshot = result.second.snapshot,
                                    )
                                    limited = true
                                } else {
                                    generated += sampled
                                    generatedThisPass = true
                                }
                            }
                        }
                        else -> {
                            val result = EffectActionEngine.apply(action, numeric)
                            numeric = result.snapshot
                            results += result
                        }
                    }
                    if (limited) break
                }
                traces += EffectExecutionStep(effect.id, evaluation, results)
                if (effect.stopFollowingEffects) stoppedBy = effect.id
                if (stoppedBy != null || limited) break
            }
            if (stoppedBy != null || limited || !generatedThisPass) {
                return EffectExecutionResult(original, numeric, traces, generated, stoppedBy, limited)
            }
        }
        // All remaining Effects were checked in the last pass. No recursive retry.
        return EffectExecutionResult(original, numeric, traces, generated, stoppedBy, true)
    }

    private fun readCurrent(
        condition: EffectCondition,
        original: EffectRollSnapshot,
        current: EffectNumericSnapshot,
    ): Int = when (condition.source) {
        EffectValueSource.PART -> {
            val id = requireNotNull(condition.partId) { "Missing Part ID" }
            requireNotNull(current.parts[id]) { "Missing Part $id" }.value(condition.scope)
        }
        EffectValueSource.ROLL -> {
            val initialTotal = requireNotNull(original.roll) { "Missing Roll" }
            val initial = original.partsById.values.sumOf { it.total }
            val modified = current.parts.values.sumOf { it.total }
            val total = Math.addExact(initialTotal.total, Math.subtractExact(modified, initial))
            when (condition.scope) {
                EffectValueScope.TOTAL -> total
                EffectValueScope.DICE_ONLY -> {
                    val diceOriginal = original.roll.components.sumOf { it.subtotal }
                    val initialDice = original.partsById.values.sumOf { part -> part.components.sumOf { it.subtotal } }
                    val currentDice = current.parts.values.sumOf { it.dice }
                    Math.addExact(diceOriginal, Math.subtractExact(currentDice, initialDice))
                }
                EffectValueScope.MODIFIERS_ONLY -> {
                    val originalModifiers = original.roll.constantTotal
                    val previous = original.partsById.values.sumOf { it.constantTotal }
                    val changed = current.parts.values.sumOf { it.modifiers }
                    Math.addExact(originalModifiers, Math.subtractExact(changed, previous))
                }
            }
        }
        EffectValueSource.VARIABLE -> {
            require(condition.scope == EffectValueScope.TOTAL) { "Variable scope must be TOTAL" }
            val name = requireNotNull(condition.variableName) { "Missing variable" }
            requireNotNull(original.variables.entries.firstOrNull {
                it.key.trim().lowercase(Locale.ROOT) == name.trim().lowercase(Locale.ROOT)
            }?.value) { "Unknown variable $name" }
        }
    }

    /**
     * Take a fresh sample solely in the logical engine. A targeted reroll replaces
     * its selected dice/Part, while roll-after adds the new sample's contribution.
     * Modifiers-only actions never sample dice: constants are deterministic.
     */
    private fun applyDiceAction(
        effectId: String,
        action: EffectAction,
        snapshot: EffectNumericSnapshot,
        expressions: Map<String, String>,
        random: Random,
    ): Triple<EffectNumericSnapshot, EffectActionResult, EffectGeneratedDice?> {
        val id = action.targetPartId
        val part = id?.let { snapshot.parts[it] }
        val before = part?.value(action.scope)
        val outcome = runCatching {
            require(id != null && part != null) { "Missing target Part" }
            val expression = action.expression.ifBlank {
                requireNotNull(expressions[id]) { "Missing resolved Part expression" }
            }
            val shape = DiceExpression.parse(expression)
            if (action.scope == EffectValueScope.MODIFIERS_ONLY) {
                require(shape.diceShape().isEmpty()) {
                    "Modifiers-only rerolls cannot consume dice RNG"
                }
            }
            val sample = shape.evaluate(random)
            val next = when (action.kind) {
                EffectActionType.REROLL -> when (action.scope) {
                    EffectValueScope.DICE_ONLY -> part.copy(
                        dice = sample.components.sumOf { it.subtotal },
                    )
                    EffectValueScope.MODIFIERS_ONLY -> part.copy(modifiers = sample.constantTotal)
                    EffectValueScope.TOTAL -> part.copy(
                        dice = sample.components.sumOf { it.subtotal },
                        modifiers = sample.constantTotal,
                        totalAdjustment = 0,
                    )
                }
                EffectActionType.ROLL_AFTER -> when (action.scope) {
                    EffectValueScope.DICE_ONLY -> part.copy(dice = Math.addExact(
                        part.dice, sample.components.sumOf { it.subtotal },
                    ))
                    EffectValueScope.MODIFIERS_ONLY -> part.copy(
                        modifiers = Math.addExact(part.modifiers, sample.constantTotal),
                    )
                    EffectValueScope.TOTAL -> part.copy(
                        dice = Math.addExact(part.dice, sample.components.sumOf { it.subtotal }),
                        modifiers = Math.addExact(part.modifiers, sample.constantTotal),
                    )
                }
                else -> error("Not a dice-sampling action")
            }
            next.total // fail on overflow without mutating the original snapshot
            val changed = snapshot.copy(parts = snapshot.parts + (id to next))
            val draw = if (sample.components.isNotEmpty()) EffectGeneratedDice(
                effectId, action.id, id, action.kind, sample,
            ) else null
            Triple(changed, next.value(action.scope), draw)
        }
        return outcome.fold(
            onSuccess = { (changed, after, drawn) ->
                Triple(changed, EffectActionResult(action.id, true, id, action.scope,
                    before, after, snapshot = changed), drawn)
            },
            onFailure = { failure ->
                Triple(snapshot, EffectActionResult(action.id, false, id, action.scope,
                    before, null, error = failure.message ?: "Roll effect failed", snapshot = snapshot), null)
            },
        )
    }
}
