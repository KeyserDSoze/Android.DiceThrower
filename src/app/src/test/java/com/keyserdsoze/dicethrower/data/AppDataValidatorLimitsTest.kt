package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.LevelRuleKind
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollLevelRule
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDataValidatorLimitsTest {
    private val hero = CharacterProfile(id = "hero", name = "Hero", level = 20)
    private val periodic = RollDefinition(
        id = "periodic", characterId = hero.id, name = "Periodic", expression = "1d6",
        levelRules = listOf(RollLevelRule("every", LevelRuleKind.EVERY_LEVELS, trigger = 1, expression = "1d6")),
    )

    private fun backupJson(data: AppData): String = JSONObject()
        .put("format", AppBackupCodec.FORMAT)
        .put("formatVersion", AppBackupCodec.FORMAT_VERSION)
        .put("data", AppDataJsonCodec.encodeData(data))
        .toString()

    private fun rejected(block: () -> Unit): Boolean =
        runCatching(block).exceptionOrNull() is IllegalArgumentException

    @Test
    fun characterLevelIsBoundedOnImportAndRestore() {
        val valid = AppData(characters = listOf(hero.copy(level = AppDataValidator.MAX_CHARACTER_LEVEL)))
        assertTrue(AppDataValidator.validate(valid).isEmpty())
        val tooHigh = AppData(characters = listOf(hero.copy(level = 10_000)))
        assertFalse(AppDataValidator.validate(tooHigh).isEmpty())
        assertTrue(rejected { AppBackupCodec.decode(backupJson(tooHigh)) })
    }

    @Test
    fun rollsThatCannotBeThrownAtTheCharacterLevelAreRejectedAtBoundaries() {
        val rollable = AppData(characters = listOf(hero), rolls = listOf(periodic))
        assertTrue(AppDataValidator.validate(rollable).isEmpty())

        // Every template is valid on its own, but level 9999 expands to 10000 dice.
        val unrollable = rollable.copy(characters = listOf(hero.copy(level = 9999)))
        assertFalse(AppDataValidator.validate(unrollable).isEmpty())
        assertTrue(rejected { AppBackupCodec.decode(backupJson(unrollable)) })

        // A modifier used as dice count must stay in 1..100 for the roll to be thrown.
        val countRoll = RollDefinition(id = "count", characterId = hero.id, name = "Count", expression = "{Dice}d6")
        val zeroDice = AppData(
            characters = listOf(hero),
            modifiers = listOf(CharacterModifier("dice", hero.id, "Dice", 0)),
            rolls = listOf(countRoll),
        )
        assertFalse(AppDataValidator.validate(zeroDice).isEmpty())
    }

    @Test
    fun storedLegacyDataKeepsLoadingAndUnrelatedEditsKeepSaving() {
        val legacy = AppData(
            characters = listOf(hero.copy(level = 10_000), CharacterProfile(id = "other", name = "Other")),
            rolls = listOf(
                periodic.copy(characterId = hero.id),
                // Previously accepted (and silently evaluated as 24); now rejected for new data.
                RollDefinition(id = "spaced", characterId = hero.id, name = "Spaced", expression = "2 {level}"),
            ),
        )
        assertFalse(AppDataValidator.validate(legacy).isEmpty())
        assertTrue(AppDataValidator.validate(legacy, acceptedBaseline = legacy).isEmpty())
        val stored = decodeStoredData(AppDataJsonCodec.encodeData(legacy).toString())
        assertEquals(legacy, stored)

        val renamedOther = legacy.copy(characters = legacy.characters.map {
            if (it.id == "other") it.copy(name = "Renamed") else it
        })
        assertTrue(AppDataValidator.validate(renamedOther, acceptedBaseline = legacy).isEmpty())

        val newRoll = RollDefinition(id = "new", characterId = "other", name = "New", expression = "3 1d6")
        assertFalse(AppDataValidator.validate(
            legacy.copy(rolls = legacy.rolls + newRoll), acceptedBaseline = legacy,
        ).isEmpty())
        val editedLegacyRoll = legacy.copy(rolls = legacy.rolls.map {
            if (it.id == "spaced") it.copy(name = "Edited") else it
        })
        assertFalse(AppDataValidator.validate(editedLegacyRoll, acceptedBaseline = legacy).isEmpty())
        val raisedLevel = legacy.copy(characters = legacy.characters.map {
            if (it.id == hero.id) it.copy(level = 10_001) else it
        })
        assertFalse(AppDataValidator.validate(raisedLevel, acceptedBaseline = legacy).isEmpty())
    }

    @Test
    fun editValidationIgnoresStaleSyncMetadataButStillRejectsUnrollableLevels() {
        val roll = RollDefinition(id = "scaling", characterId = hero.id, name = "Scaling", expression = "{level}d6")
        val stored = SyncMetadataManager.ensureMetadata(
            AppData(characters = listOf(hero), rolls = listOf(roll)), writerId = "writer", now = 1L,
        )
        assertTrue(AppDataValidator.validate(stored).isEmpty())

        fun atLevel(level: Int) = stored.copy(characters = stored.characters.map { it.copy(level = level) })
        // The proposed copy still carries the old revision until LocalStore re-stamps it.
        assertFalse(AppDataValidator.validate(atLevel(21)).isEmpty())
        assertTrue(AppDataValidator.validateEdit(atLevel(21), stored).isEmpty())
        assertTrue(AppDataValidator.validateEdit(atLevel(100), stored).isEmpty())
        assertFalse(AppDataValidator.validateEdit(atLevel(101), stored).isEmpty())
    }
}
