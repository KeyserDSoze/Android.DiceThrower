package com.keyserdsoze.dicethrower.ui.v2

import com.keyserdsoze.dicethrower.data.AppDataJsonCodec
import com.keyserdsoze.dicethrower.data.AppDataValidator
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.dice.DiceComponent
import com.keyserdsoze.dicethrower.dice.DiceRollResult
import com.keyserdsoze.dicethrower.dice.EffectRollSnapshot
import com.keyserdsoze.dicethrower.dice.EffectSequenceExecutor
import com.keyserdsoze.dicethrower.model.EffectComparison
import com.keyserdsoze.dicethrower.model.EffectValueScope
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
    fun criticalHitTemplateConnectsTwoDistinctPartsAndMultipliesOnlyDamageDice() {
        val critical = EffectEditorDraft.criticalHit(parts, 0, "Critical hit")
        val trigger = critical.activationGroups.single().conditions.single()
        val action = critical.actions.single()
        assertEquals("attack-1", trigger.partId)
        assertEquals(EffectValueScope.DICE_ONLY, trigger.scope)
        assertEquals(EffectComparison.EQUAL, trigger.comparison)
        assertEquals("20", trigger.threshold)
        assertEquals("damage-2", action.targetPartId)
        assertEquals(EffectActionType.MULTIPLY, action.kind)
        assertEquals(EffectValueScope.DICE_ONLY, action.scope)
        assertEquals("2", action.expression)
        val saved = EffectEditorDraft.canonicalize(listOf(critical), parts, 5, emptyList())
        val roll = RollDefinition(
            id = "critical-roll", characterId = "hero", name = "Critical attack",
            expression = "(1d20+2)+(2d6+3)",
            subgroups = parts,
            effects = saved,
        )
        val data = AppData(characters = listOf(CharacterProfile("hero", "Hero")), rolls = listOf(roll))
        assertTrue(AppDataValidator.validate(data).isEmpty())
        assertEquals(data, AppDataJsonCodec.decodeData(AppDataJsonCodec.encodeData(data)))

        fun run(attackFace: Int): Pair<Int, Int> {
            val attack = DiceRollResult(
                attackFace + 5, listOf(DiceComponent(1, 20, 1, listOf(attackFace))), 5,
            )
            val damage = DiceRollResult(
                11, listOf(DiceComponent(2, 6, 1, listOf(3, 4))), 4,
            )
            val result = EffectSequenceExecutor.execute(
                EffectRollSnapshot(null, mapOf("attack-1" to attack, "damage-2" to damage)),
                saved, mapOf("attack-1" to "1d20+5", "damage-2" to "2d6+4"),
            )
            assertTrue(result.steps.single().activation.activated == (attackFace == 20))
            assertEquals(attackFace + 5, result.finalSnapshot.parts.getValue("attack-1").total)
            assertEquals(4, result.finalSnapshot.parts.getValue("damage-2").modifiers)
            return result.finalSnapshot.parts.getValue("damage-2").let { it.dice to it.total }
        }
        assertEquals(14 to 18, run(20))
        assertEquals(7 to 11, run(19)) // Total attack=24 does not count as a natural 20.
    }

    @Test
    fun criticalTemplateRequiresTwoPartsAndKeepsReferencesStableAcrossRenames() {
        assertTrue(runCatching {
            EffectEditorDraft.criticalHit(parts.take(1), 0, "Critical")
        }.isFailure)
        val rule = EffectEditorDraft.criticalHit(parts, 0, "Critical")
        val renamed = parts.map { it.copy(name = "Renamed " + it.name) }
        assertEquals(rule, EffectEditorDraft.canonicalize(listOf(rule), renamed, 5, emptyList()).single())
    }

    @Test
    fun guidedModeAcceptsSimpleCrossPartCriticalAndPlainBonus() {
        val critical = EffectEditorDraft.criticalHit(parts, 0, "Critical")
        assertTrue(isGuidedEffectRule(critical, parts))
        assertTrue(isGuidedEffectRule(
            EffectEditorDraft.initial(EffectType.BONUS, parts, 1, "Bonus"), parts,
        ))
        val redirected = critical.copy(actions = critical.actions.map {
            it.copy(targetPartId = parts.first().id, expression = "3")
        })
        assertTrue(isGuidedEffectRule(redirected, parts))
        val persisted = EffectEditorDraft.canonicalize(listOf(critical), parts, 5, emptyList())
        assertEquals(critical, persisted.single())
    }

    @Test
    fun complexEffectsMustStayInAdvancedModeWithoutAnyLoss() {
        val simple = EffectEditorDraft.criticalHit(parts, 0, "Critical")
        val group = simple.activationGroups.single()
        val action = simple.actions.single()
        val complexCases = listOf(
            simple.copy(activationGroups = listOf(group, group.copy(id = "second"))),
            simple.copy(activationGroups = listOf(group.copy(conditions = group.conditions +
                EffectCondition("another", partId = parts.last().id)))),
            simple.copy(actions = listOf(action, action.copy(id = "other"))),
            simple.copy(actions = listOf(action.copy(kind = EffectActionType.REROLL,
                expression = "{level}d6"))),
            simple.copy(actions = listOf(action.copy(expression = "{parts:Damage}"))),
            simple.copy(activationGroups = listOf(group.copy(
                conditions = listOf(group.conditions.single().copy(threshold = "{level}")),
            ))),
        )
        complexCases.forEach { complex ->
            assertFalse(complex.toString(), isGuidedEffectRule(complex, parts))
        }
        assertFalse(isGuidedEffectRule(simple, parts.take(1)))
    }

    @Test
    fun rerollAndRollAfterAreGuidedAndUseTargetPartByDefault() {
        val initial = EffectEditorDraft.initial(EffectType.BONUS, parts, 0, "Bonus")
        val action = initial.actions.single().copy(targetPartId = parts.last().id)
        val reroll = nextEffectActionKind(action, EffectActionType.REROLL)
        assertEquals(EffectActionType.REROLL, reroll.kind)
        assertEquals("", reroll.expression)
        assertEquals("damage-2", reroll.targetPartId)
        assertTrue(isGuidedEffectRule(initial.copy(actions = listOf(reroll)), parts))
        assertEquals("", nextEffectActionKind(reroll, EffectActionType.ROLL_AFTER).expression)
        val customDice = reroll.copy(expression = "2d8")
        assertTrue(isGuidedEffectRule(initial.copy(actions = listOf(customDice)), parts))
        assertFalse(isGuidedEffectRule(initial.copy(actions = listOf(
            reroll.copy(expression = "{level}d8"),
        )), parts))
        val saved = EffectEditorDraft.canonicalize(
            listOf(initial.copy(actions = listOf(reroll))), parts, 5, emptyList(),
        )
        assertEquals("", saved.single().actions.single().expression)
        val backToReplace = nextEffectActionKind(reroll, EffectActionType.REPLACE)
        assertEquals("1", backToReplace.expression)
        assertTrue(isGuidedEffectRule(initial.copy(actions = listOf(backToReplace)), parts))
    }

    @Test
    fun numericReplaceIsNotAnotherDiceRoll() {
        val base = EffectEditorDraft.initial(EffectType.MALUS, parts, 0, "Malus")
        val action = base.actions.single()
        val replace = nextEffectActionKind(action, EffectActionType.REPLACE)
        assertEquals("1", replace.expression)
        assertEquals(EffectActionType.REPLACE, replace.kind)
        assertEquals(EffectValueScope.TOTAL, replace.scope)
        assertEquals(replace, EffectEditorDraft.canonicalize(
            listOf(base.copy(actions = listOf(replace))), parts, 2, emptyList(),
        ).single().actions.single())
    }

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
