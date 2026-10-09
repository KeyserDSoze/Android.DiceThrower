package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.DiceAppearanceMode
import com.keyserdsoze.dicethrower.model.DiceMaterial
import com.keyserdsoze.dicethrower.model.DiceStyle
import com.keyserdsoze.dicethrower.model.DiceTableTheme
import com.keyserdsoze.dicethrower.model.EffectAction
import com.keyserdsoze.dicethrower.model.EffectActionType
import com.keyserdsoze.dicethrower.model.EffectActivationGroup
import com.keyserdsoze.dicethrower.model.EffectCondition
import com.keyserdsoze.dicethrower.model.EffectType
import com.keyserdsoze.dicethrower.model.EffectValueScope
import com.keyserdsoze.dicethrower.model.PartReferenceAliases
import com.keyserdsoze.dicethrower.model.RollEffect
import com.keyserdsoze.dicethrower.model.LevelRuleKind
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollDiceAppearance
import com.keyserdsoze.dicethrower.model.RollGroup
import com.keyserdsoze.dicethrower.model.RollLevelRule
import com.keyserdsoze.dicethrower.model.RollLog
import com.keyserdsoze.dicethrower.model.RollSubgroup
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
        assertTrue(result.diceStyles.none { it.characterId == "character-a" })
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
        assertEquals(DiceTableTheme.OBSIDIAN, duplicate.diceTableTheme)

        val duplicatedModifiers = result.modifiers.filter { it.characterId == duplicate.id }
        val duplicatedGroups = result.groups.filter { it.characterId == duplicate.id }
        val duplicatedRolls = result.rolls.filter { it.characterId == duplicate.id }
        val duplicatedStyles = result.diceStyles.filter { it.characterId == duplicate.id }.sortedBy { it.order }

        assertEquals(1, duplicatedModifiers.size)
        assertEquals(1, duplicatedGroups.size)
        assertEquals(1, duplicatedRolls.size)
        assertEquals(2, duplicatedStyles.size)
        assertTrue(duplicatedStyles.none { copied -> data.diceStyles.any { it.id == copied.id } })
        assertEquals(duplicatedStyles.first().id, duplicate.defaultDiceStyleId)

        val duplicatedRoll = duplicatedRolls.single()
        assertEquals(duplicatedGroups.single().id, duplicatedRoll.groupId)
        assertNotEquals("roll-a", duplicatedRoll.id)
        assertEquals(1, duplicatedRoll.levelRules.size)
        assertNotEquals("rule-a", duplicatedRoll.levelRules.single().id)
        assertEquals(DiceAppearanceMode.UNIFORM, duplicatedRoll.diceAppearance.mode)
        assertEquals(duplicatedStyles.first().id, duplicatedRoll.diceAppearance.styleId)

        assertEquals(data.logs, result.logs)
        assertFalse(result.logs.any { it.characterId == duplicate.id })
        assertTrue(AppDataValidator.validate(result).isEmpty())
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

    @Test
    fun duplicateCharacterRemapsSubgroupAppearanceOverride() {
        val source = sampleData()
        val grouped = source.copy(
            rolls = source.rolls.map { roll ->
                if (roll.id != "roll-a") roll else roll.copy(
                    expression = "(6d6+{Intelligence})",
                    subgroups = listOf(RollSubgroup("damage", "Damage", "6d6+{Intelligence}")),
                    diceAppearance = RollDiceAppearance(
                        mode = DiceAppearanceMode.PER_DIE,
                        subgroupStyleIds = mapOf("damage" to "style-a"),
                    ),
                )
            },
        )
        var counter = 0

        val result = CharacterDataOperations.duplicateCharacter(
            data = grouped,
            characterId = "character-a",
            newName = "Copy",
            idFactory = { "copy-${counter++}" },
        )
        val duplicate = result.characters.last()
        val duplicatedRoll = result.rolls.single { it.characterId == duplicate.id }
        val duplicatedSubgroup = duplicatedRoll.subgroups.single()
        val duplicatedStyleId = result.diceStyles
            .first { it.characterId == duplicate.id && it.order == 0 }
            .id

        assertNotEquals("damage", duplicatedSubgroup.id)
        assertEquals(
            mapOf(duplicatedSubgroup.id to duplicatedStyleId),
            duplicatedRoll.diceAppearance.subgroupStyleIds,
        )
        assertTrue(AppDataValidator.validate(result).isEmpty())
    }

    @Test
    fun duplicateCharacterRewritesEntireEffectsGraphAndStoredPartAliases() {
        val parts = listOf(
            RollSubgroup("attack", "Attack", "1d20"),
            RollSubgroup("damage", "Damage", "2d6"),
        )
        val originalEffect = RollEffect(
            id = "effect-id", name = "Critical damage", type = EffectType.BONUS,
            activationGroups = listOf(EffectActivationGroup(
                id = "group-id",
                conditions = listOf(EffectCondition(
                    id = "condition-id", partId = "attack",
                    threshold = "{partId:damage}+f({level}/10)",
                )),
            )),
            actions = listOf(EffectAction(
                id = "action-id", kind = EffectActionType.MULTIPLY,
                targetPartId = "damage", scope = EffectValueScope.TOTAL,
                expression = "{partId:damage}*2",
            )),
        )
        val source = sampleData().let { data ->
            data.copy(rolls = data.rolls.map { roll ->
                roll.copy(
                    expression = "(1d20)+(2d6)",
                    subgroups = parts,
                    effects = listOf(originalEffect),
                )
            })
        }
        assertTrue(AppDataValidator.validate(source).isEmpty())
        var nextId = 0
        val duplicated = CharacterDataOperations.duplicateCharacter(
            source, "character-a", "Copy with Effects", idFactory = { "copy-${nextId++}" },
        )
        val copy = duplicated.rolls.single {
            it.characterId == duplicated.characters.last().id
        }
        val newParts = copy.subgroups.associateBy { it.name }
        val copiedEffect = copy.effects.single()
        val condition = copiedEffect.activationGroups.single().conditions.single()
        val action = copiedEffect.actions.single()

        assertNotEquals(originalEffect.id, copiedEffect.id)
        assertNotEquals("group-id", copiedEffect.activationGroups.single().id)
        assertNotEquals("condition-id", condition.id)
        assertNotEquals("action-id", action.id)
        assertNotEquals("attack", condition.partId)
        assertEquals(newParts.getValue("Attack").id, condition.partId)
        assertEquals(newParts.getValue("Damage").id, action.targetPartId)
        assertEquals("{partId:${newParts.getValue("Damage").id}}*2", action.expression)
        assertEquals(
            "{partId:${newParts.getValue("Damage").id}}+f({level}/10)",
            condition.threshold,
        )
        assertEquals("{parts:Damage}*2",
            PartReferenceAliases.display(action.expression, copy.subgroups))
        assertEquals(originalEffect, duplicated.rolls.single { it.id == "roll-a" }.effects.single())
        assertTrue(AppDataValidator.validate(duplicated).isEmpty())
        assertEquals(duplicated, AppDataJsonCodec.decodeData(AppDataJsonCodec.encodeData(duplicated)))
        val deletedSource = CharacterDataOperations.deleteCharacter(duplicated, "character-a")
        assertTrue(AppDataValidator.validate(deletedSource).isEmpty())
        assertEquals(1, deletedSource.rolls.size)
        assertEquals(copy, deletedSource.rolls.single())
    }

    private fun sampleData(): AppData = AppData(
        characters = listOf(
            CharacterProfile(
                id = "character-a",
                name = "Alyndra",
                tag = "Arcane",
                level = 8,
                order = 0,
                defaultDiceStyleId = "style-a",
                diceTableTheme = DiceTableTheme.OBSIDIAN,
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
                diceAppearance = RollDiceAppearance(
                    mode = DiceAppearanceMode.UNIFORM,
                    styleId = "style-a",
                ),
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
        diceStyles = listOf(
            DiceStyle(
                id = "style-a",
                characterId = "character-a",
                name = "Arcane",
                material = DiceMaterial.GLOSSY_RESIN,
                primaryColorArgb = 0xFF2563EB.toInt(),
                secondaryColorArgb = 0xFFF6C453.toInt(),
                order = 0,
            ),
            DiceStyle(
                id = "style-b",
                characterId = "character-a",
                name = "Steel",
                material = DiceMaterial.METAL,
                primaryColorArgb = 0xFF64748B.toInt(),
                secondaryColorArgb = 0xFFE2E8F0.toInt(),
                order = 1,
            ),
        ),
    )
}
