package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.dice.RollFormulaResolver
import com.keyserdsoze.dicethrower.model.AppData

object AppDataValidator {
    fun validate(data: AppData): List<String> {
        val errors = mutableListOf<String>()
        val characterIds = data.characters.map { it.id }.toSet()
        val groupById = data.groups.associateBy { it.id }

        checkUnique("character", data.characters.map { it.id }, errors)
        checkUnique("modifier", data.modifiers.map { it.id }, errors)
        checkUnique("group", data.groups.map { it.id }, errors)
        checkUnique("roll", data.rolls.map { it.id }, errors)
        checkUnique("log", data.logs.map { it.id }, errors)
        checkUnique(
            "level rule",
            data.rolls.flatMap { roll -> roll.levelRules.map { it.id } },
            errors,
        )

        data.characters.forEach { character ->
            if (character.name.isBlank()) errors += "Character ${character.id} has a blank name"
            if (character.level < 1) errors += "Character ${character.id} has an invalid level"

            val modifiers = data.modifiers.filter { it.characterId == character.id }
            val normalizedNames = modifiers.map { it.name.trim().lowercase() }
            if (normalizedNames.size != normalizedNames.toSet().size) {
                errors += "Character ${character.id} contains duplicate modifier names"
            }
            if (normalizedNames.any { it.isBlank() || it == RollFormulaResolver.LEVEL_VARIABLE }) {
                errors += "Character ${character.id} contains an invalid or reserved modifier name"
            }
        }

        data.modifiers.forEach { modifier ->
            if (modifier.characterId !in characterIds) {
                errors += "Modifier ${modifier.id} references a missing character"
            }
        }

        data.groups.forEach { group ->
            if (group.characterId !in characterIds) {
                errors += "Group ${group.id} references a missing character"
            }
            if (group.name.isBlank()) errors += "Group ${group.id} has a blank name"
        }

        data.rolls.forEach { roll ->
            val character = data.characters.firstOrNull { it.id == roll.characterId }
            if (character == null) {
                errors += "Roll ${roll.id} references a missing character"
                return@forEach
            }
            if (roll.name.isBlank()) errors += "Roll ${roll.id} has a blank name"

            val modifiers = data.modifiers.filter { it.characterId == character.id }
            if (!RollFormulaResolver.validateTemplate(roll.expression, character.level, modifiers)) {
                errors += "Roll ${roll.id} has an invalid expression"
            }

            roll.groupId?.let { groupId ->
                val group = groupById[groupId]
                if (group == null) {
                    errors += "Roll ${roll.id} references a missing group"
                } else if (group.characterId != roll.characterId) {
                    errors += "Roll ${roll.id} references a group owned by another character"
                }
            }

            roll.levelRules.forEach { rule ->
                if (rule.trigger < 1) errors += "Level rule ${rule.id} has an invalid trigger"
                if (!RollFormulaResolver.validateTemplate(rule.expression, character.level, modifiers)) {
                    errors += "Level rule ${rule.id} has an invalid expression"
                }
            }
        }

        data.logs.forEach { log ->
            if (log.characterId !in characterIds) {
                errors += "Log ${log.id} references a missing character"
            }
        }

        return errors
    }

    fun requireValid(data: AppData) {
        val errors = validate(data)
        require(errors.isEmpty()) { errors.joinToString("; ") }
    }

    private fun checkUnique(
        label: String,
        ids: List<String>,
        errors: MutableList<String>,
    ) {
        if (ids.any { it.isBlank() }) errors += "A $label has a blank id"
        if (ids.size != ids.toSet().size) errors += "Duplicate $label ids"
    }
}
