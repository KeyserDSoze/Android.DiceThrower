package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.DiceAppearanceMode
import com.keyserdsoze.dicethrower.model.DiceMaterial
import com.keyserdsoze.dicethrower.model.DiceStyle
import com.keyserdsoze.dicethrower.model.DiceStylePresets
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollDiceAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedDiceStyleCatalogTest {
    private fun style(id: String, owner: String? = null) = DiceStyle(
        id = id, characterId = owner, name = "Shared $id",
        material = DiceMaterial.METAL, primaryColorArgb = 0xFF334455.toInt(),
        secondaryColorArgb = 0xFFDDEEFF.toInt(),
    )

    private fun usedByTwoCharacters(): AppData = AppData(
        characters = listOf(
            CharacterProfile("one", "One", defaultDiceStyleId = "shared", secondaryDiceStyleId = "shared"),
            CharacterProfile("two", "Two", defaultDiceStyleId = "shared"),
        ),
        rolls = listOf(
            RollDefinition(id = "roll-one", characterId = "one", name = "Attack",
                expression = "1d20", diceAppearance = RollDiceAppearance(
                    mode = DiceAppearanceMode.UNIFORM, styleId = "shared")),
            RollDefinition(id = "roll-two", characterId = "two", name = "Damage",
                expression = "2d6", diceAppearance = RollDiceAppearance(
                    mode = DiceAppearanceMode.RANDOM_PER_DIE,
                    subgroupStyleIds = emptyMap(),
                    perDieStyleIds = mapOf("0:0" to "shared"),
                    randomStyleIds = listOf("shared"))),
        ),
        diceStyles = listOf(style("shared")),
    )

    @Test fun sharedStyleEditsUpdateBothCharactersWithoutChangingTheirId() {
        val original = usedByTwoCharacters()
        val beforeOne = CharacterRevision.revision(original, "one")
        val beforeTwo = CharacterRevision.revision(original, "two")
        val edited = SharedDiceStyleCatalog.edit(original, style("shared").copy(name = "Azure"))
        assertEquals("Azure", edited.diceStyles.single().name)
        assertEquals("shared", edited.characters.first().defaultDiceStyleId)
        assertEquals("shared", edited.characters.last().defaultDiceStyleId)
        assertEquals(beforeOne, CharacterRevision.revision(edited, "one"))
        assertEquals(beforeTwo, CharacterRevision.revision(edited, "two"))
        assertTrue(AppDataValidator.validate(edited).isEmpty())
    }

    @Test fun deletionAtomicallyReassignsEveryReference() {
        val original = usedByTwoCharacters()
        val deleted = SharedDiceStyleCatalog.delete(original, "shared")
        assertTrue(deleted.diceStyles.isEmpty())
        deleted.characters.forEach { assertEquals(DiceStylePresets.fallbackId, it.defaultDiceStyleId) }
        assertEquals(DiceStylePresets.fallbackId, deleted.characters.first().secondaryDiceStyleId)
        assertEquals(DiceStylePresets.fallbackId, deleted.rolls.first().diceAppearance.styleId)
        assertEquals(DiceStylePresets.fallbackId, deleted.rolls.last().diceAppearance.perDieStyleIds["0:0"])
        assertEquals(listOf(DiceStylePresets.fallbackId), deleted.rolls.last().diceAppearance.randomStyleIds)
        assertTrue(AppDataValidator.validate(deleted).isEmpty())
    }

    @Test fun deletingInUseStyleSelectsAnotherCustomBeforeSystemFallback() {
        val data = usedByTwoCharacters().copy(diceStyles = listOf(style("shared"), style("other")))
        val cleaned = SharedDiceStyleCatalog.delete(data, "shared")
        assertEquals("other", cleaned.characters.first().defaultDiceStyleId)
        assertEquals("other", cleaned.rolls.first().diceAppearance.styleId)
        assertTrue(AppDataValidator.validate(cleaned).isEmpty())
    }

    @Test fun migrationPromotesOldStylesPreservingIdsAndAssignments() {
        val old = usedByTwoCharacters().copy(
            diceStyles = listOf(style("shared", "one")),
            characters = listOf(CharacterProfile("one", "One", defaultDiceStyleId = "shared")),
            rolls = usedByTwoCharacters().rolls.filter { it.characterId == "one" },
        )
        val promoted = SharedDiceStyleCatalog.migrateLegacy(old)
        assertEquals(null, promoted.diceStyles.single().characterId)
        assertEquals("shared", promoted.characters.single().defaultDiceStyleId)
        assertTrue(AppDataValidator.validate(promoted).isEmpty())
        assertEquals(promoted, SharedDiceStyleCatalog.migrateLegacy(promoted))
    }

    @Test fun jsonBackupRoundTripAndLegacyMigrationRetainDefinitions() {
        val original = usedByTwoCharacters()
        val encoded = AppDataJsonCodec.encodeData(original)
        assertEquals(AppDataJsonCodec.DATA_VERSION, encoded.getInt("version"))
        val restored = AppDataJsonCodec.decodeData(encoded)
        assertEquals(original.diceStyles, restored.diceStyles)
        assertEquals(original.rolls.map { it.diceAppearance }, restored.rolls.map { it.diceAppearance })
        val legacy = AppDataJsonCodec.encodeData(original.copy(diceStyles = listOf(style("shared", "one"))))
            .put("version", 15)
        assertEquals(null, SharedDiceStyleCatalog.migrateLegacy(AppDataJsonCodec.decodeData(legacy))
            .diceStyles.single().characterId)
    }

    @Test fun catalogRevisionIndependentOfSortOrderButTracksContents() {
        val a = style("a"); val b = style("b")
        assertEquals(SharedDiceStyleCatalog.revision(listOf(a, b)),
            SharedDiceStyleCatalog.revision(listOf(b, a)))
        assertFalse(SharedDiceStyleCatalog.revision(listOf(a, b)) ==
            SharedDiceStyleCatalog.revision(listOf(a, b.copy(name = "updated"))))
    }
}
