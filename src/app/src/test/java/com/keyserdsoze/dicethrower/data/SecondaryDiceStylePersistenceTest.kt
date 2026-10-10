package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.DiceMaterial
import com.keyserdsoze.dicethrower.model.DiceStyle
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondaryDiceStylePersistenceTest {
    private val first = DiceStyle(
        id = "first", characterId = "hero", name = "Blue",
        material = DiceMaterial.GLOSSY_RESIN,
        primaryColorArgb = 0xFF2563EB.toInt(),
        secondaryColorArgb = 0xFFF6C453.toInt(),
    )
    private val second = DiceStyle(
        id = "second", characterId = "hero", name = "Amethyst",
        material = DiceMaterial.GEMSTONE,
        primaryColorArgb = 0xFF8C52E5.toInt(),
        secondaryColorArgb = 0xFF67EBDA.toInt(),
        order = 1,
    )

    @Test
    fun explicitSecondStyleSurvivesBackupAndAffectsCloudRevision() {
        val original = AppData(
            characters = listOf(CharacterProfile(
                id = "hero", name = "Hero",
                defaultDiceStyleId = "first", secondaryDiceStyleId = "second",
            )),
            diceStyles = listOf(first, second),
        )
        assertTrue(AppDataValidator.validate(original).isEmpty())
        val decoded = AppDataJsonCodec.decodeData(AppDataJsonCodec.encodeData(original))
        assertEquals(original, decoded)
        assertEquals(original, AppDataJsonCodec.decodeData(AppDataJsonCodec.encodeDataForSync(original)))
        val automatic = original.copy(characters = original.characters.map {
            it.copy(secondaryDiceStyleId = null)
        })
        assertNotEquals(CharacterRevision.revision(automatic, "hero"),
            CharacterRevision.revision(original, "hero"))
    }

    @Test
    fun oldBackupsDefaultToAutomaticWithoutChangingOldRevision() {
        val legacy = JSONObject().put("characters", org.json.JSONArray().put(
            JSONObject().put("id", "hero").put("name", "Hero")
        ))
        val decoded = AppDataJsonCodec.decodeData(legacy)
        assertNull(decoded.characters.single().secondaryDiceStyleId)
        val newContent = CharacterRevision.canonicalContent(decoded, "hero")
        assertTrue(!newContent.contains("secondaryDiceStyleId"))
    }

    @Test
    fun duplicatingCharacterRebindsItsSecondStyleToOwnedCopy() {
        val data = AppData(
            characters = listOf(CharacterProfile("hero", "Hero",
                defaultDiceStyleId = "first", secondaryDiceStyleId = "second")),
            diceStyles = listOf(first, second),
        )
        var id = 0
        val copied = CharacterDataOperations.duplicateCharacter(
            data, "hero", "Hero copy", idFactory = { "new-${id++}" },
        )
        val character = copied.characters.last()
        assertEquals("new-0", character.id)
        assertNotEquals("second", character.secondaryDiceStyleId)
        assertEquals(character.id,
            copied.diceStyles.single { it.id == character.secondaryDiceStyleId }.characterId)
        assertTrue(AppDataValidator.validate(copied).isEmpty())
    }

    @Test
    fun deletingSecondaryStyleReturnsToAutomaticPreset() {
        val data = AppData(
            characters = listOf(CharacterProfile("hero", "Hero",
                defaultDiceStyleId = "first", secondaryDiceStyleId = "second")),
            diceStyles = listOf(first, second),
        )
        assertTrue(DiceStyleDataOperations.usage(data, "second").isCharacterDefault)
        val changed = DiceStyleDataOperations.deleteStyle(data, "second")
        assertNull(changed.characters.single().secondaryDiceStyleId)
        assertEquals("first", changed.characters.single().defaultDiceStyleId)
        assertTrue(AppDataValidator.validate(changed).isEmpty())
    }

    @Test
    fun foreignSecondStyleIsRejectedByValidator() {
        val foreignStyle = second.copy(characterId = "other")
        val data = AppData(
            characters = listOf(CharacterProfile("hero", "Hero", secondaryDiceStyleId = "second"),
                CharacterProfile("other", "Other")),
            diceStyles = listOf(foreignStyle),
        )
        assertTrue(AppDataValidator.validate(data).any {
            it.contains("secondary dice style")
        })
    }
}
