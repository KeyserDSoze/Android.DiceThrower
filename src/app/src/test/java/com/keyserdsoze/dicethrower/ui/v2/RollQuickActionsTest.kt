package com.keyserdsoze.dicethrower.ui.v2

import com.keyserdsoze.dicethrower.data.AppDataValidator
import com.keyserdsoze.dicethrower.dice.RollFormulaResolver
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.RollDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RollQuickActionsTest {
    @Test
    fun quickLevelActionsChangeOnlySelectedCharacterAndPreserveRolls() {
        val mage = CharacterProfile(id = "mage", name = "Mage", level = 3)
        val warrior = CharacterProfile(id = "warrior", name = "Warrior", level = 8)
        val roll = RollDefinition(id = "roll", characterId = "mage", name = "Magic", expression = "{level}d6")
        val initial = AppData(characters = listOf(mage, warrior), rolls = listOf(roll))
        val increased = initial.withCharacterLevel("mage", 4)

        assertEquals(4, increased.characters.first().level)
        assertEquals(8, increased.characters.last().level)
        assertEquals(initial.rolls, increased.rolls)
        assertEquals(
            "4d6",
            RollFormulaResolver.resolve(increased.characters.first(), emptyList(), increased.rolls.first()).expression,
        )
        assertTrue(AppDataValidator.validate(increased).isEmpty())
    }

    @Test
    fun quickLevelActionsRespectMinAndMax() {
        val original = AppData(characters = listOf(CharacterProfile(id = "mage", name = "Mage", level = 1)))
        assertEquals(1, original.withCharacterLevel("mage", 0).characters.single().level)
        assertEquals(9999, original.withCharacterLevel("mage", 10000).characters.single().level)
        assertEquals(1, original.withCharacterLevel("missing", 8).characters.single().level)
    }

    @Test
    fun levelActionsMustBeDisabledWhenDynamicDiceCountWouldBeInvalid() {
        val character = CharacterProfile(id = "mage", name = "Mage", level = 100)
        val roll = RollDefinition(id = "scaling", characterId = character.id, name = "Scaling", expression = "{level}d6")
        val initial = AppData(characters = listOf(character), rolls = listOf(roll))
        assertTrue(AppDataValidator.validate(initial).isEmpty())
        assertFalse(AppDataValidator.validate(initial.withCharacterLevel("mage", 101)).isEmpty())
    }

    @Test
    fun editShortcutsPreserveExistingGroupReturnRoute() {
        assertEquals(RouteV2.GROUP, previousRouteFor(RouteV2.ROLL, hasRollReturnGroup = true))
        assertEquals(RouteV2.CHARACTER, previousRouteFor(RouteV2.ROLL, hasRollReturnGroup = false))
    }
}
