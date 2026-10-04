package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.DiceAppearanceMode
import com.keyserdsoze.dicethrower.model.DiceMaterial
import com.keyserdsoze.dicethrower.model.DiceStyle
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollDiceAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceStyleDataOperationsTest {
    @Test
    fun deletingStyleCleansDefaultAndRollReferences() {
        val data = AppData(
            characters = listOf(
                CharacterProfile(
                    id = "character",
                    name = "Hero",
                    defaultDiceStyleId = "red",
                ),
            ),
            diceStyles = listOf(
                style("red", 0),
                style("blue", 1),
            ),
            rolls = listOf(
                RollDefinition(
                    id = "uniform",
                    characterId = "character",
                    name = "Uniform",
                    expression = "1d20",
                    diceAppearance = RollDiceAppearance(
                        mode = DiceAppearanceMode.UNIFORM,
                        styleId = "red",
                    ),
                ),
                RollDefinition(
                    id = "per-die",
                    characterId = "character",
                    name = "Per die",
                    expression = "2d6",
                    diceAppearance = RollDiceAppearance(
                        mode = DiceAppearanceMode.PER_DIE,
                        perDieStyleIds = mapOf("0:0" to "red", "0:1" to "blue"),
                    ),
                ),
                RollDefinition(
                    id = "random",
                    characterId = "character",
                    name = "Random",
                    expression = "2d6",
                    diceAppearance = RollDiceAppearance(
                        mode = DiceAppearanceMode.RANDOM_PER_DIE,
                        randomStyleIds = listOf("red", "blue"),
                    ),
                ),
            ),
        )

        val updated = DiceStyleDataOperations.deleteStyle(data, "red")

        assertNull(updated.characters.single().defaultDiceStyleId)
        assertEquals(listOf("blue"), updated.diceStyles.map { it.id })
        assertEquals(DiceAppearanceMode.CHARACTER_DEFAULT, updated.rolls.first { it.id == "uniform" }.diceAppearance.mode)
        assertEquals(
            mapOf("0:1" to "blue"),
            updated.rolls.first { it.id == "per-die" }.diceAppearance.perDieStyleIds,
        )
        assertEquals(
            listOf("blue"),
            updated.rolls.first { it.id == "random" }.diceAppearance.randomStyleIds,
        )
        assertTrue(AppDataValidator.validate(updated).isEmpty())
    }

    @Test
    fun deletingOnlyExplicitRandomStyleFallsBackToCharacterDefault() {
        val data = AppData(
            characters = listOf(CharacterProfile(id = "character", name = "Hero")),
            diceStyles = listOf(style("red", 0), style("blue", 1)),
            rolls = listOf(
                RollDefinition(
                    id = "roll",
                    characterId = "character",
                    name = "Roll",
                    expression = "1d20",
                    diceAppearance = RollDiceAppearance(
                        mode = DiceAppearanceMode.RANDOM_UNIFORM,
                        randomStyleIds = listOf("red"),
                    ),
                ),
            ),
        )

        val updated = DiceStyleDataOperations.deleteStyle(data, "red")

        assertEquals(DiceAppearanceMode.CHARACTER_DEFAULT, updated.rolls.single().diceAppearance.mode)
        assertTrue(updated.rolls.single().diceAppearance.randomStyleIds.isEmpty())
    }

    private fun style(id: String, order: Int) = DiceStyle(
        id = id,
        characterId = "character",
        name = id,
        material = DiceMaterial.GLOSSY_RESIN,
        primaryColorArgb = 0xFF3366CC.toInt(),
        secondaryColorArgb = 0xFFFFCC66.toInt(),
        order = order,
    )
}
