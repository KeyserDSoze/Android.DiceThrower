package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.AppSettings
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollGroup
import com.keyserdsoze.dicethrower.model.RollLevelAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RollMinimumLevelTest {
    private val character = CharacterProfile(id = "hero", name = "Hero", level = 3)
    private val future = RollDefinition(
        id = "future", characterId = "hero", name = "Future ability",
        expression = "1d20", minimumLevel = 5, groupId = "abilities",
    )
    private val baseline = AppData(
        characters = listOf(character),
        groups = listOf(RollGroup(id = "abilities", characterId = "hero", name = "Abilities")),
        rolls = listOf(
            RollDefinition(id = "basic", characterId = "hero", name = "Basic", expression = "1d6"),
            future,
            future.copy(id = "disabled", minimumLevel = 1, enabled = false),
        ),
    )

    @Test
    fun futureRollIsPreservedAndAutomaticallyAppearsWhenLevelIsReached() {
        assertEquals(listOf("basic"),
            RollLevelAvailability.usable(baseline.rolls, "hero", 3).map { it.id })
        assertEquals(listOf("basic"),
            RollLevelAvailability.usable(baseline.rolls, "hero", 4).map { it.id })
        assertEquals(listOf("basic", "future"),
            RollLevelAvailability.usable(baseline.rolls, "hero", 5).map { it.id })
        assertEquals(listOf("basic", "future"),
            RollLevelAvailability.usable(baseline.rolls, "hero", 8).map { it.id })
        assertEquals(listOf("basic"),
            RollLevelAvailability.usable(baseline.rolls, "hero", 4).map { it.id })
        // The Edit screen uses unfiltered persisted rolls, including locked ones.
        assertEquals(3, baseline.rolls.size)
        assertEquals(5, baseline.rolls.first { it.id == "future" }.minimumLevel)
        assertFalse(RollLevelAvailability.isAvailable(baseline.rolls.last(), 50))
    }

    @Test
    fun groupedAndUngroupedRollsApplyTheSameAvailabilityPolicy() {
        val availableAt3 = RollLevelAvailability.usable(baseline.rolls, "hero", 3)
        assertTrue(availableAt3.none { it.groupId == "abilities" })
        val availableAt5 = RollLevelAvailability.usable(baseline.rolls, "hero", 5)
        assertTrue(availableAt5.any { it.groupId == "abilities" })
        assertEquals(listOf("future"),
            availableAt5.filter { it.groupId == "abilities" }.map { it.id })
        assertTrue(RollLevelAvailability.usable(baseline.rolls, "different", 6).isEmpty())
    }

    @Test
    fun storedMinimumSurvivesLocalBackupSyncAndCharacterCopy() {
        assertTrue(AppDataValidator.validate(baseline).isEmpty())
        val json = AppDataJsonCodec.encodeData(baseline)
        assertEquals(AppDataJsonCodec.DATA_VERSION, json.getInt("version"))
        assertEquals(baseline, AppDataJsonCodec.decodeData(json))
        assertEquals(baseline,
            AppDataJsonCodec.decodeData(AppDataJsonCodec.encodeDataForSync(baseline)))
        val backup = AppBackupCodec.decode(AppBackupCodec.encode(baseline, AppSettings(), "it"))
        assertEquals(baseline, backup.data)

        var id = 0
        val copied = CharacterDataOperations.duplicateCharacter(
            baseline, "hero", "Clone", idFactory = { "clone-${id++}" },
        )
        val cloneId = copied.characters.last().id
        assertEquals(5, copied.rolls.first { it.characterId == cloneId &&
            it.name == future.name }.minimumLevel)
        assertTrue(AppDataValidator.validate(copied).isEmpty())
    }

    @Test
    fun missingLegacyLevelDefaultsToOneAndLeavesRevisionUnchanged() {
        val legacy = baseline.copy(rolls = baseline.rolls.map { it.copy(minimumLevel = 1) })
        val json = AppDataJsonCodec.encodeData(legacy)
        val rolls = json.getJSONArray("rolls")
        for (index in 0 until rolls.length()) {
            rolls.getJSONObject(index).remove("minimumLevel")
        }
        json.put("version", 13)
        val restored = AppDataJsonCodec.decodeData(json)
        assertEquals(legacy, restored)
        assertTrue(restored.rolls.all { it.minimumLevel == 1 })
        assertEquals(CharacterRevision.revision(legacy, "hero"),
            CharacterRevision.revision(restored, "hero"))
        val changed = legacy.copy(rolls = legacy.rolls.map {
            if (it.id == "future") it.copy(minimumLevel = 5) else it
        })
        assertNotEquals(CharacterRevision.revision(legacy, "hero"),
            CharacterRevision.revision(changed, "hero"))
    }

    @Test
    fun outOfRangeMinimumIsRejectedDuringRestore() {
        for (invalid in listOf(-10, 0, 10000)) {
            assertTrue(AppDataValidator.validate(baseline.copy(
                rolls = listOf(future.copy(minimumLevel = invalid)),
            )).any { it.contains("invalid minimum level") })
            val json = AppDataJsonCodec.encodeData(baseline)
            json.getJSONArray("rolls").getJSONObject(1).put("minimumLevel", invalid)
            assertEquals(invalid, AppDataJsonCodec.decodeData(json).rolls[1].minimumLevel)
            // Validated stores/backups reject out-of-range values rather than
            // interpreting them as an unlocked ability.
            assertFalse(RollLevelAvailability.isAvailable(future.copy(minimumLevel = 10000), 9999))
        }
    }

    @Test
    fun userCanPrepareFirstLevelOnlyAndManualDisableIsIndependent() {
        val unlockedByDefault = RollDefinition(
            id = "always", characterId = "hero", name = "Always", expression = "1d4",
        )
        assertTrue(RollLevelAvailability.isAvailable(unlockedByDefault, 1))
        assertFalse(RollLevelAvailability.isAvailable(unlockedByDefault, 0))
        assertFalse(RollLevelAvailability.isAvailable(
            unlockedByDefault.copy(enabled = false), 9999))
    }
}
