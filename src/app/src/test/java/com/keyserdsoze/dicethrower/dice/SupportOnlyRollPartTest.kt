package com.keyserdsoze.dicethrower.dice

import com.keyserdsoze.dicethrower.data.AppBackupCodec
import com.keyserdsoze.dicethrower.data.AppDataJsonCodec
import com.keyserdsoze.dicethrower.data.AppDataValidator
import com.keyserdsoze.dicethrower.data.CharacterRevision
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.AppSettings
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.DoubleRollMode
import com.keyserdsoze.dicethrower.model.EffectAction
import com.keyserdsoze.dicethrower.model.EffectActionType
import com.keyserdsoze.dicethrower.model.EffectActivationGroup
import com.keyserdsoze.dicethrower.model.EffectComparison
import com.keyserdsoze.dicethrower.model.EffectCondition
import com.keyserdsoze.dicethrower.model.EffectType
import com.keyserdsoze.dicethrower.model.EffectValueScope
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollEffect
import com.keyserdsoze.dicethrower.model.RollSubgroup
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SupportOnlyRollPartTest {
    private val hero = CharacterProfile(id = "hero", name = "Hero")
    private val attack = RollSubgroup(
        id = "attack", name = "Attack", expression = "1d20+3", includeInDoubleRoll = true,
    )
    private val reserve = RollSubgroup(
        id = "reserve", name = "Extra damage", expression = "2d6",
        includeInNormalRoll = false,
    )
    private val parts = listOf(attack, reserve)
    private val roll = RollDefinition(
        id = "attack-roll", characterId = hero.id, name = "Attack",
        expression = RollFormulaResolver.canonicalExpression(parts),
        subgroups = parts,
    )

    @Test
    fun inactivePartDoesNotConsumeInitialRngOrParticipateInBestWorst() {
        val resolved = RollFormulaResolver.resolve(hero, emptyList(), roll)
        assertEquals("(1d20+3)", resolved.expression)
        assertEquals(listOf("attack"), resolved.subgroups.map { it.id })
        assertEquals(listOf("reserve"), resolved.supportSubgroups.map { it.id })
        val normal = DoubleRollEngine.evaluate(
            resolved, DoubleRollMode.NORMAL, setOf("attack", "reserve"), Random(7),
        )
        assertEquals(1, normal.result.components.size)
        assertEquals(1, normal.parts.size)
        val best = DoubleRollEngine.evaluate(
            resolved, DoubleRollMode.BEST, setOf("attack", "reserve"), Random(7),
        )
        assertEquals(1, best.parts.size)
        val snapshot = EffectRollSnapshot.fromDoubleRoll(best, supportSubgroups = resolved.supportSubgroups)
        assertEquals(0, snapshot.partsById.getValue("reserve").total)
        assertFalse(best.parts.any { it.subgroup.id == "reserve" })
    }

    @Test
    fun conditionalRollAfterSamplesDormantPartAndRerollReplacesItsDice() {
        val resolved = RollFormulaResolver.resolve(hero, emptyList(), roll)
        val base = DoubleRollEngine.evaluate(resolved, DoubleRollMode.NORMAL, emptySet(), Random(11))
        val original = EffectRollSnapshot.fromDoubleRoll(base, supportSubgroups = resolved.supportSubgroups)
        val formulae = (resolved.subgroups + resolved.supportSubgroups).associate { it.id to it.expression }
        val action = EffectAction("extra", EffectActionType.ROLL_AFTER, "reserve",
            EffectValueScope.DICE_ONLY, "")
        val effect = RollEffect(
            id = "bonus", name = "Bonus damage", type = EffectType.BONUS,
            activationGroups = listOf(EffectActivationGroup("when", listOf(
                EffectCondition("cond", partId = "attack", scope = EffectValueScope.DICE_ONLY,
                    comparison = EffectComparison.GREATER_OR_EQUAL, threshold = "1"),
            ))),
            actions = listOf(action),
        )
        val after = EffectSequenceExecutor.execute(original, listOf(effect), formulae, Random(5))
        assertTrue(after.steps.single().activation.activated)
        assertTrue(after.generatedDice.isNotEmpty())
        assertEquals("reserve", after.generatedDice.single().partId)
        assertEquals(2, after.generatedDice.single().result.components.single().rolls.size)
        val extra = after.finalSnapshot.parts.getValue("reserve").dice
        assertTrue(extra in 2..12)
        assertEquals(base.result.total + extra,
            EffectRuntimeHistory.adjustedLegacyTotal(after, base.result.total))
        assertEquals(1, EffectRuntimeHistory.supportPartResults(after, resolved.supportSubgroups).size)

        val miss = effect.copy(activationGroups = listOf(
            effect.activationGroups.single().copy(conditions = listOf(
                effect.activationGroups.single().conditions.single().copy(
                    comparison = EffectComparison.GREATER, threshold = "20",
                ),
            )),
        ))
        val skipped = EffectSequenceExecutor.execute(original, listOf(miss), formulae, Random(5))
        assertTrue(skipped.generatedDice.isEmpty())
        assertEquals(0, skipped.finalSnapshot.parts.getValue("reserve").total)
        assertTrue(EffectRuntimeHistory.supportPartResults(skipped, resolved.supportSubgroups).isEmpty())
        assertEquals(base.result.total, EffectRuntimeHistory.adjustedLegacyTotal(skipped, base.result.total))

        val rerollEffect = effect.copy(actions = listOf(action.copy(
            kind = EffectActionType.REROLL, id = "reroll",
        )))
        val rerolled = EffectSequenceExecutor.execute(original, listOf(rerollEffect), formulae, Random(5))
        assertEquals(2, rerolled.generatedDice.single().result.components.single().rolls.size)
        assertEquals(rerolled.generatedDice.single().result.components.single().subtotal,
            rerolled.finalSnapshot.parts.getValue("reserve").dice)
    }

    @Test
    fun savedPartFlagRoundTripsAndLegacyDefaultsRemainRevisionStable() {
        val data = AppData(characters = listOf(hero), rolls = listOf(roll))
        assertTrue(AppDataValidator.validate(data).isEmpty())
        assertEquals(data, AppDataJsonCodec.decodeData(AppDataJsonCodec.encodeData(data)))
        val backup = AppBackupCodec.decode(AppBackupCodec.encode(data, AppSettings(), "en"))
        assertEquals(data, backup.data)

        val normalParts = parts.map { it.copy(includeInNormalRoll = true) }
        val legacy = roll.copy(subgroups = normalParts,
            expression = RollFormulaResolver.canonicalExpression(normalParts))
        val previous = AppData(characters = listOf(hero), rolls = listOf(legacy))
        val beforeRevision = CharacterRevision.revision(previous, hero.id)
        val legacyJson = AppDataJsonCodec.encodeData(previous)
        val partRecords = legacyJson.getJSONArray("rolls")
            .getJSONObject(0).getJSONArray("subgroups")
        for (index in 0 until partRecords.length()) {
            partRecords.getJSONObject(index).remove("includeInNormalRoll")
        }
        val restored = AppDataJsonCodec.decodeData(legacyJson)
        assertEquals(previous, restored)
        assertEquals(beforeRevision, CharacterRevision.revision(restored, hero.id))
        assertNotEquals(beforeRevision, CharacterRevision.revision(data, hero.id))
    }

    @Test
    fun negativeSupportPartOnlySamplesWhenTriggeredAndKeepsItsSign() {
        val negative = reserve.copy(operator =
            com.keyserdsoze.dicethrower.model.RollSubgroupOperator.SUBTRACT)
        val formula = RollFormulaResolver.resolve(
            hero, emptyList(), roll.copy(subgroups = listOf(attack, negative)),
        )
        val negativeFormula = formula.effectPartExpressions().getValue("reserve")
        assertEquals("-(2d6)", negativeFormula)
        assertEquals(-1, DiceExpression.parse(negativeFormula).diceShape().single().sign)
        val original = EffectRollSnapshot.fromResolvedRoll(
            formula, DiceExpression.parse(formula.expression).evaluate(Random(10)),
        )
        val condition = EffectCondition("always", partId = "attack",
            comparison = EffectComparison.GREATER_OR_EQUAL, threshold = "1")
        val effect = RollEffect(
            id = "malus", name = "Penalty", type = EffectType.MALUS,
            activationGroups = listOf(EffectActivationGroup("group", listOf(condition))),
            actions = listOf(EffectAction("a", EffectActionType.ROLL_AFTER,
                "reserve", EffectValueScope.DICE_ONLY, "")),
        )
        val output = EffectSequenceExecutor.execute(
            original, listOf(effect), formula.effectPartExpressions(), Random(10),
        )
        assertTrue(output.finalSnapshot.parts.getValue("reserve").dice < 0)
        assertTrue(output.generatedDice.single().result.components.single().sign < 0)
    }

    @Test
    fun rejectsRollWithoutAnInitialPart() {
        val hidden = parts.map { it.copy(includeInNormalRoll = false) }
        assertTrue(runCatching { RollFormulaResolver.canonicalExpression(hidden) }.isFailure)
        val invalid = roll.copy(subgroups = hidden)
        assertTrue(AppDataValidator.validate(
            AppData(characters = listOf(hero), rolls = listOf(invalid)),
        ).isNotEmpty())
    }
}
