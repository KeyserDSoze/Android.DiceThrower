package com.keyserdsoze.dicethrower.dice

import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.LevelRuleKind
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollLevelRule
import com.keyserdsoze.dicethrower.model.RollSubgroup
import com.keyserdsoze.dicethrower.model.RollSubgroupOperator
import java.util.Locale

data class ResolvedRollFormula(
    val expression: String,
    val appliedRules: List<RollLevelRule>,
    val subgroups: List<ResolvedRollSubgroup> = emptyList(),
    /** Valid configured Parts that must not consume RNG in the initial throw. */
    val supportSubgroups: List<ResolvedRollSubgroup> = emptyList(),
)

data class ResolvedRollSubgroup(
    val id: String,
    val name: String,
    val operator: RollSubgroupOperator,
    val expression: String,
)

/**
 * Existing normal Parts keep their logical formula; dormant subtracting Parts
 * carry a negative sample when invoked by an Effect. An explicit action dice
 * expression still takes precedence over this default.
 */
fun ResolvedRollFormula.effectPartExpressions(): Map<String, String> {
    val supportIds = supportSubgroups.map { it.id }.toSet()
    return (subgroups + supportSubgroups).associate { part ->
        part.id to if (part.id in supportIds &&
            part.operator == RollSubgroupOperator.SUBTRACT) "-(${part.expression})"
        else part.expression
    }
}

data class ResolvedRollSubgroupResult(
    val subgroup: ResolvedRollSubgroup,
    val result: DiceRollResult,
)

fun ResolvedRollFormula.subgroupResults(outcome: DiceRollResult): List<ResolvedRollSubgroupResult> {
    var componentIndex = 0
    return subgroups.map { subgroup ->
        val parsed = DiceExpression.parse(subgroup.expression)
        val componentCount = parsed.diceShape().size
        val components = outcome.components.drop(componentIndex).take(componentCount)
        componentIndex += componentCount
        val operatorScale = if (subgroup.operator == RollSubgroupOperator.SUBTRACT) -1 else 1
        val constant = parsed.constantTotal() * operatorScale
        val result = DiceRollResult(
            total = components.sumOf { it.subtotal } + constant,
            components = components,
            constantTotal = constant,
        )
        ResolvedRollSubgroupResult(subgroup, result)
    }
}

fun ResolvedRollFormula.subgroupIdByComponentIndex(): Map<Int, String> = buildMap {
    var componentIndex = 0
    subgroups.forEach { subgroup ->
        val componentCount = DiceExpression.parse(subgroup.expression).diceShape().size
        repeat(componentCount) {
            put(componentIndex++, subgroup.id)
        }
    }
}

object RollFormulaResolver {
    const val LEVEL_VARIABLE = "level"

    // Escape both braces explicitly. Android's ICU regex engine rejects a bare closing
    // brace here even though the desktop JVM regex engine accepts it.
    private val variableRegex = Regex("""\{([^{}]+)\}""")

    fun resolve(
        character: CharacterProfile,
        modifiers: List<CharacterModifier>,
        roll: RollDefinition,
    ): ResolvedRollFormula {
        val characterModifiers = modifiers.filter { it.characterId == character.id }
        val variables = buildVariableMap(character.level, characterModifiers)
        val resolvedSubgroups = roll.subgroups.map { subgroup ->
            ResolvedRollSubgroup(
                id = subgroup.id,
                name = subgroup.name,
                operator = subgroup.operator,
                expression = resolveExpression(subgroup.expression, variables),
            )
        }
        val activeSubgroups = if (roll.subgroups.isEmpty()) emptyList() else
            resolvedSubgroups.filterIndexed { index, _ -> roll.subgroups[index].includeInNormalRoll }
        val supportSubgroups = if (roll.subgroups.isEmpty()) emptyList() else
            resolvedSubgroups.filterIndexed { index, _ -> !roll.subgroups[index].includeInNormalRoll }
        val baseExpression = if (resolvedSubgroups.isEmpty()) {
            resolveExpression(roll.expression, variables)
        } else {
            canonicalExpression(
                roll.subgroups.filter { it.includeInNormalRoll }.map { subgroup ->
                    val expression = resolvedSubgroups.first { it.id == subgroup.id }.expression
                    subgroup.copy(expression = expression)
                },
            )
        }
        val pieces = mutableListOf(baseExpression)
        val appliedRules = mutableListOf<RollLevelRule>()

        roll.levelRules.forEach { rule ->
            require(rule.trigger >= 1) { "Level rule trigger must be at least 1" }
            val repeatCount = when (rule.kind) {
                LevelRuleKind.FROM_LEVEL -> if (character.level >= rule.trigger) 1 else 0
                LevelRuleKind.EVERY_LEVELS -> character.level / rule.trigger
            }

            repeat(repeatCount) {
                pieces += resolveExpression(rule.expression, variables)
            }
            if (repeatCount > 0) {
                appliedRules += rule
            }
        }

        val combined = pieces.fold("") { current, piece ->
            appendExpression(current, piece)
        }
        val parsed = DiceExpression.parse(combined)

        return ResolvedRollFormula(
            expression = parsed.source,
            appliedRules = appliedRules,
            subgroups = activeSubgroups,
            supportSubgroups = supportSubgroups,
        )
    }

    fun canonicalExpression(subgroups: List<RollSubgroup>): String {
        val normalParts = subgroups.filter { it.includeInNormalRoll }
        require(normalParts.isNotEmpty()) { "At least one Roll Part must be included in the initial throw" }
        return normalParts.mapIndexed { index, subgroup ->
            require(subgroup.expression.isNotBlank()) { "Roll subgroup expression cannot be blank" }
            val wrapped = "(${subgroup.expression.trim()})"
            when {
                index == 0 && subgroup.operator == RollSubgroupOperator.ADD -> wrapped
                subgroup.operator == RollSubgroupOperator.SUBTRACT -> "-$wrapped"
                else -> "+$wrapped"
            }
        }.joinToString("")
    }

    fun validateTemplate(
        expression: String,
        level: Int,
        modifiers: List<CharacterModifier>,
    ): Boolean = runCatching {
        resolveTemplate(expression, level, modifiers)
    }.isSuccess

    fun resolveTemplate(
        expression: String,
        level: Int,
        modifiers: List<CharacterModifier>,
    ): String {
        val variables = buildVariableMap(level, modifiers)
        return DiceExpression.parse(resolveExpression(expression, variables)).source
    }

    fun variableNames(modifiers: List<CharacterModifier>): List<String> =
        listOf(LEVEL_VARIABLE) + modifiers.map { it.name }

    private fun buildVariableMap(
        level: Int,
        modifiers: List<CharacterModifier>,
    ): Map<String, Int> {
        require(level >= 1) { "Level must be at least 1" }

        val variables = linkedMapOf(
            normalizeName(LEVEL_VARIABLE) to level,
        )

        modifiers.forEach { modifier ->
            val key = normalizeName(modifier.name)
            require(key.isNotBlank()) { "Modifier name cannot be blank" }
            require(key != normalizeName(LEVEL_VARIABLE)) {
                "The name level is reserved"
            }
            require(key !in variables) {
                "Duplicate modifier name: ${modifier.name}"
            }
            variables[key] = modifier.value
        }

        return variables
    }

    private fun resolveExpression(
        raw: String,
        variables: Map<String, Int>,
    ): String {
        // A number immediately before a variable is multiplication, not digit concatenation:
        // legacy "2{level}" must resolve as "2x4", never as "24".
        // "{level}d6" intentionally remains a dynamic dice count.
        val withExplicitMultiplication = Regex("""(?<=[0-9)}])(?=\{)""").replace(raw, "x")
        var resolved = variableRegex.replace(withExplicitMultiplication) { match ->
            val requested = normalizeName(match.groupValues[1])
            variables[requested]?.toString()
                ?: throw IllegalArgumentException("Unknown variable: ${match.groupValues[1]}")
        }

        while (
            resolved.contains("+-") ||
            resolved.contains("-+") ||
            resolved.contains("++") ||
            resolved.contains("--")
        ) {
            resolved = resolved
                .replace("+-", "-")
                .replace("-+", "-")
                .replace("++", "+")
                .replace("--", "+")
        }

        return DiceExpression.parse(resolved).source
    }

    private fun appendExpression(
        current: String,
        addition: String,
    ): String {
        if (current.isBlank()) return addition
        return if (addition.startsWith("-")) current + addition else "$current+$addition"
    }

    private fun normalizeName(name: String): String =
        name.trim().lowercase(Locale.ROOT)
}
