package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.AppSettings
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.DoubleRollMode
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollLog
import com.keyserdsoze.dicethrower.model.RollLogPart
import com.keyserdsoze.dicethrower.model.RollSubgroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleRollPersistenceTest {
    private val roll = RollDefinition(
        id = "roll",
        characterId = "hero",
        name = "Attack + Damage",
        expression = "(1d20)+(2d6)",
        doubleRollEnabled = true,
        subgroups = listOf(
            RollSubgroup("attack", "Attack", "1d20", includeInDoubleRoll = true),
            RollSubgroup("damage", "Damage", "2d6"),
        ),
    )

    @Test
    fun configurationAndAlternativesSurviveBackupAndRestore() {
        val log = RollLog(
            id = "log", characterId = "hero", rollDefinitionId = "roll",
            rollName = roll.name, expression = roll.expression, total = 22,
            detail = "selected", timestamp = 123L,
            doubleRollMode = DoubleRollMode.BEST,
            comparisonTotal = 22,
            alternativeComparisonTotal = 13,
            parts = listOf(
                RollLogPart("Attack", "1d20", 17, "1d20[17]", 8, "1d20[8]"),
                RollLogPart("Damage", "2d6", 5, "2d6[2,3]", 5, "2d6[1,4]"),
            ),
        )
        val data = AppData(
            characters = listOf(CharacterProfile("hero", "Hero")),
            rolls = listOf(roll),
            logs = listOf(log),
        )
        val settings = AppSettings(doubleRollDirectionalSwipeEnabled = false)
        val restored = AppBackupCodec.decode(AppBackupCodec.encode(data, settings, "it"))
        assertEquals(data, restored.data)
        assertEquals(settings, restored.settings)
        assertEquals(22, restored.data.logs.single().comparisonTotal)
    }

    @Test
    fun syncRevisionIncludesDoubleRollFlagsWithoutBreakingLegacyDefaults() {
        val basic = AppData(
            characters = listOf(CharacterProfile("hero", "Hero")),
            rolls = listOf(roll.copy(doubleRollEnabled = false,
                subgroups = roll.subgroups.map { it.copy(includeInDoubleRoll = false) })),
        )
        val basicHash = CharacterRevision.revision(basic, "hero")
        assertNotEquals(basicHash, CharacterRevision.revision(
            basic.copy(rolls = listOf(basic.rolls.single().copy(doubleRollEnabled = true))), "hero",
        ))
        assertNotEquals(basicHash, CharacterRevision.revision(
            basic.copy(rolls = listOf(basic.rolls.single().copy(
                subgroups = listOf(basic.rolls.single().subgroups.first().copy(includeInDoubleRoll = true)) +
                    basic.rolls.single().subgroups.drop(1),
            ))), "hero",
        ))
    }

    @Test
    fun legacyDataAndSettingsDoNotUnexpectedlyActivateDoubleRolls() {
        val old = AppDataJsonCodec.encodeData(AppData(rolls = listOf(roll)))
        val serializedRoll = old.getJSONArray("rolls").getJSONObject(0)
        serializedRoll.remove("doubleRollEnabled")
        val subgroups = serializedRoll.getJSONArray("subgroups")
        for (i in 0 until subgroups.length()) {
            subgroups.getJSONObject(i).remove("includeInDoubleRoll")
        }
        val decoded = AppDataJsonCodec.decodeData(old).rolls.single()
        assertFalse(decoded.doubleRollEnabled)
        assertTrue(decoded.subgroups.none { it.includeInDoubleRoll })

        val settings = AppDataJsonCodec.encodeSettings(AppSettings())
        settings.remove("doubleRollDirectionalSwipeEnabled")
        assertTrue(AppDataJsonCodec.decodeSettings(settings).doubleRollDirectionalSwipeEnabled)
    }
}
