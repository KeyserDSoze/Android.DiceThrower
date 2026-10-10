package com.keyserdsoze.dicethrower.model

import com.keyserdsoze.dicethrower.data.AppDataValidator
import com.keyserdsoze.dicethrower.data.DiceStyleDataOperations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceStylePresetsTest {
    @Test
    fun catalogHasTenStableUniquePalettesAndMultipleMaterials() {
        assertEquals(10, DiceStylePresets.all.size)
        assertEquals(10, DiceStylePresets.all.map { it.key }.toSet().size)
        assertEquals(10, DiceStylePresets.all.map { it.primaryColorArgb to it.secondaryColorArgb }.toSet().size)
        assertEquals(DiceMaterial.entries.toSet(), DiceStylePresets.all.map { it.material }.toSet())
        assertEquals("arcane-blue", DiceStylePresets.all.first().key)
        assertEquals("ancient-copper", DiceStylePresets.all.last().key)
        assertTrue(DiceStylePresets.all.all { it.key.matches(Regex("[a-z]+(-[a-z]+)+")) })
        assertEquals(null, DiceStylePresets.find("missing"))
    }

    @Test
    fun presetCreatesEditableCharacterStyleWithoutChangingOtherCharacters() {
        val a = CharacterProfile("a", "A")
        val b = CharacterProfile("b", "B")
        var data = AppData(characters = listOf(a, b))
        val preset = DiceStylePresets.find("violet-star")!!
        val first = preset.instantiate("a", "a-style", "Violet Star")
        data = DiceStyleDataOperations.createStyle(data, first)
        assertEquals("a-style", data.characters.first().defaultDiceStyleId)
        assertEquals(null, data.characters.last().defaultDiceStyleId)
        val updated = DiceStyleDataOperations.updateStyle(
            data, first.copy(name = "Personalized Violet", primaryColorArgb = 0xFF5432AA.toInt()),
        )
        assertEquals("Personalized Violet", updated.diceStyles.single().name)
        assertEquals(preset.primaryColorArgb, DiceStylePresets.find("violet-star")!!.primaryColorArgb)
        data = DiceStyleDataOperations.createStyle(
            updated,
            preset.instantiate("b", "b-style", "Violet Star"),
        )
        assertTrue(AppDataValidator.validate(data).isEmpty())
        assertNotEquals(data.diceStyles.first().id, data.diceStyles.last().id)
        assertNotEquals(data.diceStyles.first().primaryColorArgb, data.diceStyles.last().primaryColorArgb)
        assertFalse(data.diceStyles.last().characterId == "a")
        assertNotNull(DiceStylePresets.find("arcane-blue"))
    }
}
