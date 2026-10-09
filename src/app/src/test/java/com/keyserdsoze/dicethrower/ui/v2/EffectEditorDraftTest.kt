package com.keyserdsoze.dicethrower.ui.v2

import com.keyserdsoze.dicethrower.data.AppDataJsonCodec
import com.keyserdsoze.dicethrower.data.AppDataValidator
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.EffectActionType
import com.keyserdsoze.dicethrower.model.EffectCondition
import com.keyserdsoze.dicethrower.model.EffectType
import com.keyserdsoze.dicethrower.model.PartReferenceAliases
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollSubgroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectEditorDraftTest {
    private val parts = listOf(
        RollSubgroup("attack-1", "Attack", "1d20+2"),
        RollSubgroup("damage-2", "Damage", "2d6+3"),
    )

    @Test
    fun bonusAndMalusDefaultsStartValidAndTargetStableFirstPart() {
        val bonus = EffectEditorDraft.initial(EffectType.BONUS, parts, 0, "Bonus")
        val malus = EffectEditorDraft.initial(EffectType.MALUS, parts, 1, "Malus")
        assertEquals(EffectActionType.ADD, bonus.actions.single().kind)
        assertEquals(EffectActionType.SUBTRACT, malus.actions.single().kind)
        assertEquals("attack-1", bonus.actions.single().targetPartId)
        assertEquals("attack-1", malus.activationGroups.single().conditions.single().partId)
        val saved = EffectEditorDraft.canonicalize(listOf(malus, bonus), parts, 10, emptyList())
        assertEquals(listOf(0, 1), saved.map { it.order })
        assertEquals(listOf("Malus", "Bonus"), saved.map { it.name })
    }

    @Test
    fun renamedPartsKeepReferencesAndRoundTrip() {
        val initial = EffectEditorDraft.initial(EffectType.BONUS, parts, 0, "Power")
        val changed = initial.copy(
            activationGroups = initial.activationGroups.map { group ->
                group.copy(conditions = group.conditions.map { condition ->
                    condition.copy(threshold = "{parts:Damage}+f({level}/10)")
                })
            },
            actions = initial.actions.map { it.copy(
                targetPartId = "damage-2", expression = "{parts:Damage}*2",
            ) },
        )
        val saved = EffectEditorDraft.canonicalize(listOf(changed), parts, 20, emptyList()).single()
        assertEquals("{partId:damage-2}*2", saved.actions.single().expression)
        assertEquals("{partId:damage-2}+f({level}/10)",
            saved.activationGroups.single().conditions.single().threshold)
        val renamed = parts.map { if (it.id == "damage-2") it.copy(name = "Fire") else it }
        assertEquals("{parts:Fire}*2",
            PartReferenceAliases.display(saved.actions.single().expression, renamed))
        val roll = RollDefinition(
            id = "roll", characterId = "hero", name = "Roll",
            expression = "(1d20+2)+(2d6+3)",
            subgroups = renamed,
            effects = listOf(saved),
        )
        val data = AppData(
            characters = listOf(CharacterProfile(id = "hero", name = "Hero")),
            rolls = listOf(roll),
        )
        assertTrue(AppDataValidator.validate(data).isEmpty())
        assertEquals(data, AppDataJsonCodec.decodeData(AppDataJsonCodec.encodeData(data)))
    }

    @Test
    fun incompleteConditionsAndDeletedTargetsBlockSave() {
        val initial = EffectEditorDraft.initial(EffectType.BONUS, parts, 0, "Bonus")
        val invalid = listOf(
            initial.copy(activationGroups = emptyList()),
            initial.copy(actions = emptyList()),
            initial.copy(actions = initial.actions.map { it.copy(targetPartId = "missing") }),
            initial.copy(actions = initial.actions.map { it.copy(expression = "1/0") }),
            initial.copy(activationGroups = initial.activationGroups.map { group ->
                group.copy(conditions = listOf(EffectCondition("broken", partId = "missing")))
            }),
            initial.copy(activationGroups = initial.activationGroups.map { group ->
                group.copy(conditions = listOf(EffectCondition("unknown", partId = "attack-1",
                    threshold = "{parts:Missing}")))
            }),
        )
        invalid.forEach { draft ->
            assertTrue(draft.toString(), runCatching {
                EffectEditorDraft.canonicalize(listOf(draft), parts, 20, emptyList())
            }.isFailure)
        }
        val duplicateNames = parts.map { it.copy(name = "Same") }
        assertTrue(runCatching {
            EffectEditorDraft.canonicalize(
                listOf(initial.copy(actions = initial.actions.map {
                    it.copy(expression = "{parts:Same}")
                })), duplicateNames, 20, emptyList(),
            )
        }.isFailure)
    }

    @Test
    fun rerollAndRollAfterAcceptDiceExpressionsButNotArithmeticDice() {
        val base = EffectEditorDraft.initial(EffectType.MALUS, parts, 0, "Malus")
        for (kind in listOf(EffectActionType.REROLL, EffectActionType.ROLL_AFTER)) {
            val valid = base.copy(actions = listOf(base.actions.first().copy(kind = kind,
                expression = "1d6")))
            assertEquals(kind,
                EffectEditorDraft.canonicalize(listOf(valid), parts, 7, emptyList())
                    .single().actions.single().kind)
            val invalid = valid.copy(actions = valid.actions.map { it.copy(expression = "a(5)") })
            assertTrue(runCatching {
                EffectEditorDraft.canonicalize(listOf(invalid), parts, 7, emptyList())
            }.isFailure)
        }
    }
}
