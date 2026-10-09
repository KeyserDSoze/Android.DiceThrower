package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.RollDiceAppearance
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
            diceStyles = data.diceStyles.filterNot { it.characterId == characterId },
            characterSyncMetadata = data.characterSyncMetadata.filterNot { it.characterId == characterId },
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
        val styleIdMap = data.diceStyles
            .filter { it.characterId == characterId }
            .associate { it.id to idFactory() }
        val groupIdMap = data.groups
            .filter { it.characterId == characterId }
            .associate { it.id to idFactory() }

        val duplicatedCharacter = source.copy(
            id = newCharacterId,
            name = newName.trim(),
            order = data.characters.size,
            defaultDiceStyleId = source.defaultDiceStyleId?.let(styleIdMap::get),
        )

        val duplicatedStyles = data.diceStyles
            .filter { it.characterId == characterId }
            .map { style ->
                style.copy(
                    id = styleIdMap.getValue(style.id),
                    characterId = newCharacterId,
                )
            }

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
                val subgroupIdMap = roll.subgroups.associate { subgroup -> subgroup.id to idFactory() }
                roll.copy(
                    id = idFactory(),
                    characterId = newCharacterId,
                    groupId = roll.groupId?.let(groupIdMap::get),
                    diceAppearance = roll.diceAppearance
                        .remapStyles(styleIdMap)
                        .remapSubgroups(subgroupIdMap),
                    levelRules = roll.levelRules.map { rule ->
                        rule.copy(id = idFactory())
                    },
                    subgroups = roll.subgroups.map { subgroup ->
                        subgroup.copy(id = subgroupIdMap.getValue(subgroup.id))
                    },
                    // Every copied Effect and its nested rules get independent IDs.
                    // References to Parts MUST follow the copied subgroup ID map,
                    // including tokens embedded in saved threshold/action formulas.
                    effects = roll.effects.map { effect ->
                        effect.copy(
                            id = idFactory(),
                            activationGroups = effect.activationGroups.map { group ->
                                group.copy(
                                    id = idFactory(),
                                    conditions = group.conditions.map { condition ->
                                        condition.copy(
                                            id = idFactory(),
                                            partId = condition.partId?.let(subgroupIdMap::getValue),
                                            threshold = condition.threshold.remapEffectPartIds(subgroupIdMap),
                                        )
                                    },
                                )
                            },
                            actions = effect.actions.map { action ->
                                action.copy(
                                    id = idFactory(),
                                    targetPartId = action.targetPartId?.let(subgroupIdMap::getValue),
                                    expression = action.expression.remapEffectPartIds(subgroupIdMap),
                                )
                            },
                        )
                    },
                )
            }

        return data.copy(
            characters = data.characters + duplicatedCharacter,
            modifiers = data.modifiers + duplicatedModifiers,
            groups = data.groups + duplicatedGroups,
            rolls = data.rolls + duplicatedRolls,
            diceStyles = data.diceStyles + duplicatedStyles,
            // Roll history is intentionally not copied. A duplicate starts with a clean history.
            logs = data.logs,
        )
    }

    private val effectPartToken = Regex("""\{partId:([^{}]+)\}""")

    private fun String.remapEffectPartIds(ids: Map<String, String>): String =
        effectPartToken.replace(this) { found ->
            val previous = found.groupValues[1]
            "{partId:${ids.getValue(previous)}}"
        }


    private fun RollDiceAppearance.remapStyles(styleIdMap: Map<String, String>): RollDiceAppearance = copy(
        styleId = styleId?.let(styleIdMap::get),
        subgroupStyleIds = subgroupStyleIds.mapValues { (_, styleId) -> styleIdMap.getValue(styleId) },
        perDieStyleIds = perDieStyleIds.mapValues { (_, styleId) -> styleIdMap.getValue(styleId) },
        randomStyleIds = randomStyleIds.map(styleIdMap::getValue),
    )

    private fun RollDiceAppearance.remapSubgroups(subgroupIdMap: Map<String, String>): RollDiceAppearance = copy(
        subgroupStyleIds = subgroupStyleIds.mapKeys { (subgroupId, _) -> subgroupIdMap.getValue(subgroupId) },
    )
}
