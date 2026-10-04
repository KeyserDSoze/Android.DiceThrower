package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import java.util.UUID

object CharacterDataOperations {
    fun deleteCharacter(
        data: AppData,
        characterId: String,
    ): AppData {
        if (data.characters.none { it.id == characterId }) return data

        val remainingCharacters = data.characters
            .filterNot { it.id == characterId }
            .sortedBy { it.order }
            .mapIndexed { index, character -> character.copy(order = index) }

        return data.copy(
            characters = remainingCharacters,
            modifiers = data.modifiers.filterNot { it.characterId == characterId },
            groups = data.groups.filterNot { it.characterId == characterId },
            rolls = data.rolls.filterNot { it.characterId == characterId },
            logs = data.logs.filterNot { it.characterId == characterId },
        )
    }

    fun duplicateCharacter(
        data: AppData,
        characterId: String,
        newName: String,
        idFactory: () -> String = { UUID.randomUUID().toString() },
    ): AppData {
        val source = data.characters.firstOrNull { it.id == characterId } ?: return data
        require(newName.isNotBlank()) { "Duplicated character name cannot be blank" }

        val newCharacterId = idFactory()
        val groupIdMap = data.groups
            .filter { it.characterId == characterId }
            .associate { it.id to idFactory() }

        val duplicatedCharacter = source.copy(
            id = newCharacterId,
            name = newName.trim(),
            order = data.characters.size,
        )

        val duplicatedModifiers = data.modifiers
            .filter { it.characterId == characterId }
            .map { modifier ->
                modifier.copy(
                    id = idFactory(),
                    characterId = newCharacterId,
                )
            }

        val duplicatedGroups = data.groups
            .filter { it.characterId == characterId }
            .map { group ->
                group.copy(
                    id = groupIdMap.getValue(group.id),
                    characterId = newCharacterId,
                )
            }

        val duplicatedRolls = data.rolls
            .filter { it.characterId == characterId }
            .map { roll ->
                roll.copy(
                    id = idFactory(),
                    characterId = newCharacterId,
                    groupId = roll.groupId?.let(groupIdMap::get),
                    levelRules = roll.levelRules.map { rule ->
                        rule.copy(id = idFactory())
                    },
                )
            }

        return data.copy(
            characters = data.characters + duplicatedCharacter,
            modifiers = data.modifiers + duplicatedModifiers,
            groups = data.groups + duplicatedGroups,
            rolls = data.rolls + duplicatedRolls,
            // Roll history is intentionally not copied. A duplicate starts with a clean history.
            logs = data.logs,
        )
    }
}
