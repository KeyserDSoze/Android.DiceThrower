package com.keyserdsoze.dicethrower.dice

import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.LevelRuleKind
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollLevelRule
import java.util.Locale

data class ResolvedRollFormula(
    val expression: String,
    val appliedRules: List<RollLevelRule>,
)

object RollFormulaResolver {
    const val LEVEL_VARIABLE = "level"

    private val variableRegex = Regex("""\{([^{}]+)}""")

    fun resolve(
        character: CharacterProfile,
        modifiers: List<CharacterModifier>,
        roll: RollDefinition,
    ): ResolvedRollFormula {
        val characterModifiers = modifiers.filter { it.characterId == character.id }
        val variables = buildVariableMap(character.level, characterModifiers)
        val pieces = mutableListOf(resolveExpression(roll.expression, variables))
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
        )
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
        var resolved = variableRegex.replace(raw) { match ->
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
