package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.LevelRuleKind
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollGroup
import com.keyserdsoze.dicethrower.model.RollLevelRule
import com.keyserdsoze.dicethrower.model.RollLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterDataOperationsTest {
    @Test
    fun deleteCharacterRemovesAllOwnedDataAndCompactsOrder() {
        val data = sampleData()

        val result = CharacterDataOperations.deleteCharacter(data, "character-a")

        assertEquals(listOf("character-b"), result.characters.map { it.id })
        assertEquals(0, result.characters.single().order)
        assertTrue(result.modifiers.none { it.characterId == "character-a" })
        assertTrue(result.groups.none { it.characterId == "character-a" })
        assertTrue(result.rolls.none { it.characterId == "character-a" })
        assertTrue(result.logs.none { it.characterId == "character-a" })
        assertEquals(1, result.logs.size)
        assertEquals("character-b", result.logs.single().characterId)
    }

    @Test
    fun duplicateCharacterRemapsEveryRelationshipAndStartsWithoutHistory() {
        val data = sampleData()
        var counter = 0
        val idFactory = { "new-${counter++}" }

        val result = CharacterDataOperations.duplicateCharacter(
            data = data,
            characterId = "character-a",
            newName = "Alyndra Copy",
            idFactory = idFactory,
        )

        assertEquals(3, result.characters.size)
        val duplicate = result.characters.last()
        assertEquals("Alyndra Copy", duplicate.name)
        assertEquals(data.characters.size, duplicate.order)
        assertNotEquals("character-a", duplicate.id)
        assertEquals(data.characters.first().level, duplicate.level)

        val duplicatedModifiers = result.modifiers.filter { it.characterId == duplicate.id }
        val duplicatedGroups = result.groups.filter { it.characterId == duplicate.id }
        val duplicatedRolls = result.rolls.filter { it.characterId == duplicate.id }

        assertEquals(1, duplicatedModifiers.size)
        assertEquals(1, duplicatedGroups.size)
        assertEquals(1, duplicatedRolls.size)

        val duplicatedRoll = duplicatedRolls.single()
        assertEquals(duplicatedGroups.single().id, duplicatedRoll.groupId)
        assertNotEquals("roll-a", duplicatedRoll.id)
        assertEquals(1, duplicatedRoll.levelRules.size)
        assertNotEquals("rule-a", duplicatedRoll.levelRules.single().id)

        assertEquals(data.logs, result.logs)
        assertFalse(result.logs.any { it.characterId == duplicate.id })
    }

    @Test
    fun missingCharacterLeavesDataUntouched() {
        val data = sampleData()
        assertEquals(data, CharacterDataOperations.deleteCharacter(data, "missing"))
        assertEquals(
            data,
            CharacterDataOperations.duplicateCharacter(
                data = data,
                characterId = "missing",
                newName = "Copy",
            ),
        )
    }

    private fun sampleData(): AppData = AppData(
        characters = listOf(
            CharacterProfile(
                id = "character-a",
                name = "Alyndra",
                tag = "Arcane",
                level = 8,
                order = 0,
            ),
            CharacterProfile(
                id = "character-b",
                name = "Borin",
                tag = "Dungeon",
                level = 3,
                order = 1,
            ),
        ),
        modifiers = listOf(
            CharacterModifier(
                id = "modifier-a",
                characterId = "character-a",
                name = "Intelligence",
                value = 4,
            ),
        ),
        groups = listOf(
            RollGroup(
                id = "group-a",
                characterId = "character-a",
                name = "Attack Spells",
            ),
        ),
        rolls = listOf(
            RollDefinition(
                id = "roll-a",
                characterId = "character-a",
                name = "Fireball",
                expression = "6d6+{Intelligence}",
                groupId = "group-a",
                levelRules = listOf(
                    RollLevelRule(
                        id = "rule-a",
                        kind = LevelRuleKind.FROM_LEVEL,
                        trigger = 10,
                        expression = "1d6",
                    ),
                ),
            ),
        ),
        logs = listOf(
            RollLog(
                id = "log-a",
                characterId = "character-a",
                rollDefinitionId = "roll-a",
                rollName = "Fireball",
                expression = "6d6+4",
                total = 22,
                detail = "6d6[3,4,2,5,4,0] +4",
                timestamp = 10L,
            ),
            RollLog(
                id = "log-b",
                characterId = "character-b",
                rollDefinitionId = "roll-b",
                rollName = "Guard",
                expression = "1d20+2",
                total = 14,
                detail = "1d20[12] +2",
                timestamp = 20L,
            ),
        ),
    )
}
