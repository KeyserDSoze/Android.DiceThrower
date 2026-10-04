package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.DiceAppearanceMode
import com.keyserdsoze.dicethrower.model.RollDiceAppearance

object DiceStyleDataOperations {
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

    private fun RollDiceAppearance.withoutStyle(styleId: String): RollDiceAppearance {
        val cleanedPerDie = perDieStyleIds.filterValues { it != styleId }
        val hadExplicitRandomPool = randomStyleIds.isNotEmpty()
        val cleanedRandom = randomStyleIds.filterNot { it == styleId }

        if (mode == DiceAppearanceMode.UNIFORM && this.styleId == styleId) {
            return RollDiceAppearance()
        }
        if (mode == DiceAppearanceMode.PER_DIE && cleanedPerDie.isEmpty()) {
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
            perDieStyleIds = cleanedPerDie,
            randomStyleIds = cleanedRandom,
        )
    }
}
