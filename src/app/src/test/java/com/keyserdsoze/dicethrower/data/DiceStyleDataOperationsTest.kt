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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceStyleDataOperationsTest {
    @Test
    fun crossCharacterCopyCreatesIndependentOwnedStylesAndUniqueNames() {
        val sourceStyle = style("steel", 0).copy(name = "Steel", material = DiceMaterial.METAL)
        val destinationSteel = DiceStyle(
            id = "existing",
            characterId = "destination",
            name = "Steel",
            material = DiceMaterial.MATTE_RESIN,
            primaryColorArgb = 0xFF111111.toInt(),
            secondaryColorArgb = 0xFFEEEEEE.toInt(),
            order = 0,
        )
        val data = AppData(
            characters = listOf(
                CharacterProfile(id = "character", name = "Source", defaultDiceStyleId = "steel"),
                CharacterProfile(id = "destination", name = "Destination", defaultDiceStyleId = "existing"),
            ),
            diceStyles = listOf(sourceStyle, destinationSteel),
        )

        val copied = DiceStyleDataOperations.copyStylesFromCharacter(
            data = data,
            sourceCharacterId = "character",
            destinationCharacterId = "destination",
            styleIds = setOf("steel"),
            copySourceDefault = false,
            copySuffix = "copy",
            idFactory = { "copied-steel" },
        )

        val imported = copied.diceStyles.single { it.id == "copied-steel" }
        assertEquals("destination", imported.characterId)
        assertEquals("Steel copy", imported.name)
        assertEquals(sourceStyle.material, imported.material)
        assertEquals(sourceStyle.primaryColorArgb, imported.primaryColorArgb)
        assertEquals(sourceStyle.secondaryColorArgb, imported.secondaryColorArgb)
        assertEquals(1, imported.order)
        assertEquals("existing", copied.characters.single { it.id == "destination" }.defaultDiceStyleId)
        assertEquals(sourceStyle, copied.diceStyles.single { it.id == "steel" })
        assertTrue(AppDataValidator.validate(copied).isEmpty())

        val editedCopy = DiceStyleDataOperations.updateStyle(copied, imported.copy(name = "Destination Steel"))
        val sourceDeleted = DiceStyleDataOperations.deleteStyle(editedCopy, "steel")
        assertEquals("Destination Steel", sourceDeleted.diceStyles.single { it.id == "copied-steel" }.name)
        assertTrue(sourceDeleted.diceStyles.none { it.id == "steel" })
        assertTrue(AppDataValidator.validate(sourceDeleted).isEmpty())
    }

    @Test
    fun selectedSourceDefaultCanBecomeDestinationDefaultWithFreshId() {
        val data = AppData(
            characters = listOf(
                CharacterProfile(id = "character", name = "Source", defaultDiceStyleId = "red"),
                CharacterProfile(id = "destination", name = "Destination"),
            ),
            diceStyles = listOf(style("red", 0), style("blue", 1)),
        )
        var nextId = 0

        val copied = DiceStyleDataOperations.copyStylesFromCharacter(
            data = data,
            sourceCharacterId = "character",
            destinationCharacterId = "destination",
            styleIds = setOf("red", "blue"),
            copySourceDefault = true,
            copySuffix = "copy",
            idFactory = { "import-${nextId++}" },
        )

        val destination = copied.characters.single { it.id == "destination" }
        val imported = copied.diceStyles.filter { it.characterId == "destination" }.sortedBy { it.order }
        assertEquals(listOf("import-0", "import-1"), imported.map { it.id })
        assertEquals("import-0", destination.defaultDiceStyleId)
        assertTrue(AppDataValidator.validate(copied).isEmpty())
    }

    @Test
    fun copiedNamesAdvanceCaseInsensitivelyWithoutCollision() {
        val existing = listOf("Steel", "Steel Copy", "steel copy 2")

        assertEquals("Steel Copy 3", DiceStyleDataOperations.uniqueCopyName(existing, "Steel", "Copy"))
        assertEquals("Gemstone", DiceStyleDataOperations.uniqueCopyName(existing, "Gemstone", "Copy"))
    }

    @Test
    fun firstCreatedStyleBecomesDefaultAndLaterStylesAppendInOrder() {
        val data = AppData(characters = listOf(CharacterProfile(id = "character", name = "Hero")))

        val withFirst = DiceStyleDataOperations.createStyle(data, style("first", 99))
        val withSecond = DiceStyleDataOperations.createStyle(withFirst, style("second", 99))

        assertEquals("first", withSecond.characters.single().defaultDiceStyleId)
        assertEquals(listOf(0, 1), withSecond.diceStyles.map { it.order })
        assertTrue(AppDataValidator.validate(withSecond).isEmpty())
    }

    @Test
    fun updatingStyleKeepsIdentityOwnershipOrderAndRollReferences() {
        val original = style("red", 2)
        val data = AppData(
            characters = listOf(CharacterProfile(id = "character", name = "Hero", defaultDiceStyleId = "red")),
            diceStyles = listOf(original),
            rolls = listOf(
                RollDefinition(
                    id = "roll",
                    characterId = "character",
                    name = "Roll",
                    expression = "1d20",
                    diceAppearance = RollDiceAppearance(mode = DiceAppearanceMode.UNIFORM, styleId = "red"),
                ),
            ),
        )

        val updated = DiceStyleDataOperations.updateStyle(
            data,
            original.copy(name = "Crimson", material = DiceMaterial.METAL, order = 50),
        )

        assertEquals("Crimson", updated.diceStyles.single().name)
        assertEquals(DiceMaterial.METAL, updated.diceStyles.single().material)
        assertEquals(2, updated.diceStyles.single().order)
        assertEquals("red", updated.rolls.single().diceAppearance.styleId)
        assertEquals("red", updated.characters.single().defaultDiceStyleId)
    }

    @Test
    fun duplicateAppendsIndependentStyleAndMoveOnlyReordersItsCharacter() {
        val other = DiceStyle(
            id = "other",
            characterId = "other-character",
            name = "Other",
            material = DiceMaterial.MATTE_RESIN,
            primaryColorArgb = 0xFF111111.toInt(),
            secondaryColorArgb = 0xFFEEEEEE.toInt(),
            order = 0,
        )
        val data = AppData(
            characters = listOf(
                CharacterProfile(id = "character", name = "Hero"),
                CharacterProfile(id = "other-character", name = "Other"),
            ),
            diceStyles = listOf(style("red", 0), style("blue", 1), other),
        )

        val duplicated = DiceStyleDataOperations.duplicateStyle(data, "red", "copy", "Red copy")
        val moved = DiceStyleDataOperations.moveStyle(duplicated, "character", "copy", -2)

        assertEquals(listOf("copy", "red", "blue"), moved.diceStyles.filter { it.characterId == "character" }.sortedBy { it.order }.map { it.id })
        assertEquals(0, moved.diceStyles.single { it.id == "other" }.order)
        assertFalse(moved.characters.single { it.id == "character" }.defaultDiceStyleId == "copy")
    }

    @Test
    fun usageReportsDefaultAndAllReferencingRolls() {
        val data = AppData(
            characters = listOf(CharacterProfile(id = "character", name = "Hero", defaultDiceStyleId = "red")),
            diceStyles = listOf(style("red", 0)),
            rolls = listOf(
                RollDefinition(
                    id = "uniform",
                    characterId = "character",
                    name = "Uniform",
                    expression = "1d20",
                    diceAppearance = RollDiceAppearance(mode = DiceAppearanceMode.UNIFORM, styleId = "red"),
                ),
                RollDefinition(
                    id = "random",
                    characterId = "character",
                    name = "Random",
                    expression = "1d6",
                    diceAppearance = RollDiceAppearance(mode = DiceAppearanceMode.RANDOM_UNIFORM, randomStyleIds = listOf("red")),
                ),
            ),
        )

        val usage = DiceStyleDataOperations.usage(data, "red")

        assertTrue(usage.isCharacterDefault)
        assertEquals(setOf("uniform", "random"), usage.referencingRollIds)
        assertTrue(usage.isInUse)
    }

    @Test
    fun characterDefaultCanBeChangedWithoutChangingRollDefinitions() {
        val roll = RollDefinition(
            id = "roll",
            characterId = "character",
            name = "Roll",
            expression = "1d20",
        )
        val data = AppData(
            characters = listOf(CharacterProfile(id = "character", name = "Hero", defaultDiceStyleId = "red")),
            diceStyles = listOf(style("red", 0), style("blue", 1)),
            rolls = listOf(roll),
        )

        val updated = DiceStyleDataOperations.setDefaultStyle(data, "character", "blue")

        assertEquals("blue", updated.characters.single().defaultDiceStyleId)
        assertEquals(roll, updated.rolls.single())
        assertTrue(AppDataValidator.validate(updated).isEmpty())
    }

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
