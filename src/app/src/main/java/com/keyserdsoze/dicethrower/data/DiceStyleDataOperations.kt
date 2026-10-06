package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.DiceAppearanceMode
import com.keyserdsoze.dicethrower.model.DiceStyle
import com.keyserdsoze.dicethrower.model.RollDiceAppearance
import java.util.UUID

data class DiceStyleUsage(
    val isCharacterDefault: Boolean,
    val referencingRollIds: Set<String>,
) {
    val isInUse: Boolean get() = isCharacterDefault || referencingRollIds.isNotEmpty()
}

object DiceStyleDataOperations {
    fun copyStylesFromCharacter(
        data: AppData,
        sourceCharacterId: String,
        destinationCharacterId: String,
        styleIds: Set<String>,
        copySourceDefault: Boolean,
        copySuffix: String,
        idFactory: () -> String = { UUID.randomUUID().toString() },
    ): AppData {
        require(sourceCharacterId != destinationCharacterId) { "Source and destination characters must differ" }
        require(data.characters.any { it.id == destinationCharacterId }) { "Destination character does not exist" }
        val sourceCharacter = data.characters.firstOrNull { it.id == sourceCharacterId }
            ?: return data
        val sourceStyles = data.diceStyles
            .filter { it.characterId == sourceCharacterId && it.id in styleIds }
            .sortedBy { it.order }
        if (sourceStyles.isEmpty()) return data

        val destinationStyles = data.diceStyles.filter { it.characterId == destinationCharacterId }
        val names = destinationStyles.mapTo(mutableListOf()) { it.name }
        var nextOrder = (destinationStyles.maxOfOrNull { it.order } ?: -1) + 1
        val idMap = linkedMapOf<String, String>()
        val copied = sourceStyles.map { source ->
            val copiedName = uniqueCopyName(names, source.name, copySuffix)
            names += copiedName
            val copiedId = idFactory()
            idMap[source.id] = copiedId
            source.copy(
                id = copiedId,
                characterId = destinationCharacterId,
                name = copiedName,
                order = nextOrder++,
            )
        }

        val mappedDefault = if (copySourceDefault) {
            sourceCharacter.defaultDiceStyleId?.let(idMap::get)
        } else {
            null
        }
        return data.copy(
            characters = if (mappedDefault == null) {
                data.characters
            } else {
                data.characters.map { character ->
                    if (character.id == destinationCharacterId) {
                        character.copy(defaultDiceStyleId = mappedDefault)
                    } else {
                        character
                    }
                }
            },
            diceStyles = data.diceStyles + copied,
        )
    }

    fun uniqueCopyName(existingNames: Collection<String>, sourceName: String, copySuffix: String): String {
        if (existingNames.none { it.equals(sourceName, ignoreCase = true) }) return sourceName
        val base = "$sourceName $copySuffix"
        if (existingNames.none { it.equals(base, ignoreCase = true) }) return base
        var index = 2
        while (existingNames.any { it.equals("$base $index", ignoreCase = true) }) index += 1
        return "$base $index"
    }

    fun createStyle(data: AppData, style: DiceStyle): AppData {
        require(data.characters.any { it.id == style.characterId }) { "Dice style owner does not exist" }
        require(data.diceStyles.none { it.id == style.id }) { "Dice style ID already exists" }
        require(style.name.isNotBlank()) { "Dice style name cannot be blank" }

        val owned = data.diceStyles.filter { it.characterId == style.characterId }
        val created = style.copy(order = (owned.maxOfOrNull { it.order } ?: -1) + 1)
        val updatedCharacters = if (owned.isEmpty()) {
            data.characters.map { character ->
                if (character.id == style.characterId && character.defaultDiceStyleId == null) {
                    character.copy(defaultDiceStyleId = created.id)
                } else {
                    character
                }
            }
        } else {
            data.characters
        }
        return data.copy(
            characters = updatedCharacters,
            diceStyles = data.diceStyles + created,
        )
    }

    fun updateStyle(data: AppData, style: DiceStyle): AppData {
        val existing = data.diceStyles.firstOrNull { it.id == style.id } ?: return data
        require(existing.characterId == style.characterId) { "Dice style ownership cannot change" }
        require(style.name.isNotBlank()) { "Dice style name cannot be blank" }
        return data.copy(
            diceStyles = data.diceStyles.map { current ->
                if (current.id == style.id) style.copy(order = existing.order) else current
            },
        )
    }

    fun duplicateStyle(data: AppData, styleId: String, newId: String, newName: String): AppData {
        val source = data.diceStyles.firstOrNull { it.id == styleId } ?: return data
        return createStyle(
            data,
            source.copy(
                id = newId,
                name = newName.trim(),
            ),
        )
    }

    fun setDefaultStyle(data: AppData, characterId: String, styleId: String): AppData {
        val style = data.diceStyles.firstOrNull { it.id == styleId }
        require(style?.characterId == characterId) { "Default style must belong to the character" }
        return data.copy(
            characters = data.characters.map { character ->
                if (character.id == characterId) character.copy(defaultDiceStyleId = styleId) else character
            },
        )
    }

    fun moveStyle(data: AppData, characterId: String, styleId: String, delta: Int): AppData {
        val owned = data.diceStyles.filter { it.characterId == characterId }.sortedBy { it.order }.toMutableList()
        val from = owned.indexOfFirst { it.id == styleId }
        if (from < 0) return data
        val to = (from + delta).coerceIn(0, owned.lastIndex)
        if (from == to) return data

        val moving = owned.removeAt(from)
        owned.add(to, moving)
        val reordered = owned.mapIndexed { index, style -> style.copy(order = index) }.associateBy { it.id }
        return data.copy(
            diceStyles = data.diceStyles.map { style -> reordered[style.id] ?: style },
        )
    }

    fun usage(data: AppData, styleId: String): DiceStyleUsage {
        val style = data.diceStyles.firstOrNull { it.id == styleId }
            ?: return DiceStyleUsage(isCharacterDefault = false, referencingRollIds = emptySet())
        val character = data.characters.firstOrNull { it.id == style.characterId }
        val rollIds = data.rolls
            .filter { it.characterId == style.characterId && it.diceAppearance.references(styleId) }
            .mapTo(linkedSetOf()) { it.id }
        return DiceStyleUsage(
            isCharacterDefault = character?.defaultDiceStyleId == styleId,
            referencingRollIds = rollIds,
        )
    }

    fun deleteStyle(data: AppData, styleId: String): AppData {
        val style = data.diceStyles.firstOrNull { it.id == styleId } ?: return data

        val updatedCharacters = data.characters.map { character ->
            if (character.id == style.characterId && character.defaultDiceStyleId == styleId) {
                character.copy(defaultDiceStyleId = null)
            } else {
                character
            }
        }

        val updatedRolls = data.rolls.map { roll ->
            if (roll.characterId != style.characterId) return@map roll
            roll.copy(diceAppearance = roll.diceAppearance.withoutStyle(styleId))
        }

        return data.copy(
            characters = updatedCharacters,
            rolls = updatedRolls,
            diceStyles = data.diceStyles.filterNot { it.id == styleId },
        )
    }

    private fun RollDiceAppearance.references(styleId: String): Boolean =
        this.styleId == styleId ||
            styleId in subgroupStyleIds.values ||
            styleId in perDieStyleIds.values ||
            styleId in randomStyleIds

    private fun RollDiceAppearance.withoutStyle(styleId: String): RollDiceAppearance {
        val cleanedSubgroups = subgroupStyleIds.filterValues { it != styleId }
        val cleanedPerDie = perDieStyleIds.filterValues { it != styleId }
        val hadExplicitRandomPool = randomStyleIds.isNotEmpty()
        val cleanedRandom = randomStyleIds.filterNot { it == styleId }

        if (mode == DiceAppearanceMode.UNIFORM && this.styleId == styleId) {
            return RollDiceAppearance()
        }
        if (mode == DiceAppearanceMode.PER_DIE && cleanedSubgroups.isEmpty() && cleanedPerDie.isEmpty()) {
            return RollDiceAppearance()
        }
        if (
            (mode == DiceAppearanceMode.RANDOM_UNIFORM || mode == DiceAppearanceMode.RANDOM_PER_DIE) &&
            hadExplicitRandomPool && cleanedRandom.isEmpty()
        ) {
            return RollDiceAppearance()
        }

        return copy(
            styleId = this.styleId.takeUnless { it == styleId },
            subgroupStyleIds = cleanedSubgroups,
            perDieStyleIds = cleanedPerDie,
            randomStyleIds = cleanedRandom,
        )
    }
}
