package com.keyserdsoze.dicethrower.dice

import com.keyserdsoze.dicethrower.data.AppBackupCodec
import com.keyserdsoze.dicethrower.data.AppDataJsonCodec
import com.keyserdsoze.dicethrower.data.CharacterRevision
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.AppSettings
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.EffectAction
import com.keyserdsoze.dicethrower.model.EffectActionType
import com.keyserdsoze.dicethrower.model.EffectActivationGroup
import com.keyserdsoze.dicethrower.model.EffectCondition
import com.keyserdsoze.dicethrower.model.EffectType
import com.keyserdsoze.dicethrower.model.EffectValueScope
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollEffect
import com.keyserdsoze.dicethrower.model.RollLog
import com.keyserdsoze.dicethrower.model.RollLogPart
import com.keyserdsoze.dicethrower.model.RollSubgroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EffectRuntimeHistoryTest {
    private val roll = RollDefinition(
        id = "roll", characterId = "hero", name = "Attack and damage",
        expression = "(1d20)+(1d6)",
        subgroups = listOf(
            RollSubgroup("attack", "Attack", "1d20"),
            RollSubgroup("damage", "Damage", "1d6"),
        ),
    )
    private val bonus = RollEffect(
        id = "bonus", name = "Power hit", type = EffectType.BONUS,
        activationGroups = listOf(
            EffectActivationGroup("conditions", listOf(
                EffectCondition("natural20", partId = "attack", threshold = "20"),
            )),
        ),
        actions = listOf(
            EffectAction("double", EffectActionType.MULTIPLY, "damage",
                EffectValueScope.TOTAL, "2"),
        ),
    )
    private val snapshot = EffectRollSnapshot(
        roll = DiceRollResult(
            total = 24, components = listOf(
                DiceComponent(1, 20, 1, listOf(20)),
                DiceComponent(1, 6, 1, listOf(4)),
            ), constantTotal = 0,
        ),
        partsById = mapOf(
            "attack" to DiceRollResult(20, listOf(DiceComponent(1, 20, 1, listOf(20))), 0),
            "damage" to DiceRollResult(4, listOf(DiceComponent(1, 6, 1, listOf(4))), 0),
        ),
    )

    @Test
    fun effectChangesDamageButDoesNotAlterOriginalFaces() {
        val result = EffectSequenceExecutor.execute(
            original = snapshot, effects = listOf(bonus),
            resolvedPartExpressions = mapOf("attack" to "1d20", "damage" to "1d6"),
            random = Random(8),
        )
        assertEquals(8, result.finalSnapshot.parts.getValue("damage").total)
        assertEquals(4, snapshot.partsById.getValue("damage").total)
        assertEquals(28, EffectRuntimeHistory.adjustedLegacyTotal(result, 24))
        val traces = EffectRuntimeHistory.steps(result, listOf(bonus))
        assertEquals(1, traces.size)
        assertEquals("Power hit", traces.single().name)
        assertTrue(traces.single().activated)
        assertEquals(20, traces.single().conditions.single().actual)
        assertEquals(20, traces.single().conditions.single().threshold)
        assertTrue(traces.single().conditions.single().passed)
        assertEquals(4, traces.single().actions.single().before)
        assertEquals(8, traces.single().actions.single().after)
    }

    @Test
    fun historicTraceSurvivesBackupAndCloudCodecAndChangesRevision() {
        val result = EffectSequenceExecutor.execute(snapshot, listOf(bonus),
            mapOf("attack" to "1d20", "damage" to "1d6"))
        val trace = EffectRuntimeHistory.steps(result, listOf(bonus))
        val log = RollLog(
            id = "log", characterId = "hero", rollDefinitionId = "roll",
            rollName = roll.name, expression = roll.expression,
            total = 28, detail = snapshot.roll!!.detail(), timestamp = 171L,
            parts = listOf(
                RollLogPart("Attack", "1d20", 20, "1d20[20]"),
                RollLogPart("Damage", "1d6", 8, "1d6[4]",
                    originalTotal = 4, originalDetail = "1d6[4]"),
            ),
            effectSteps = trace,
        )
        val data = AppData(
            characters = listOf(CharacterProfile("hero", "Hero")),
            rolls = listOf(roll.copy(effects = listOf(bonus))),
            logs = listOf(log),
        )
        val restored = AppBackupCodec.decode(AppBackupCodec.encode(data, AppSettings(), "it"))
        assertEquals(data, restored.data)
        assertEquals(data, AppDataJsonCodec.decodeData(AppDataJsonCodec.encodeDataForSync(data)))
        assertNotEquals(
            CharacterRevision.revision(data, "hero"),
            CharacterRevision.revision(data.copy(logs = listOf(log.copy(effectSteps = emptyList()))), "hero"),
        )

        val legacyJson = AppDataJsonCodec.encodeData(data)
        val oldLog = legacyJson.getJSONArray("logs").getJSONObject(0)
        oldLog.remove("effectSteps")
        oldLog.getJSONArray("parts").getJSONObject(1).remove("originalTotal")
        oldLog.getJSONArray("parts").getJSONObject(1).remove("originalDetail")
        val old = AppDataJsonCodec.decodeData(legacyJson).logs.single()
        assertTrue(old.effectSteps.isEmpty())
        assertEquals(null, old.parts[1].originalTotal)
    }

    @Test
    fun nonActivatedRulesAreStillRecordedWithoutMutatingTotals() {
        val impossible = bonus.copy(activationGroups = listOf(
            EffectActivationGroup("no", listOf(
                EffectCondition("too-high", partId = "attack", threshold = "21"),
            )),
        ))
        val result = EffectSequenceExecutor.execute(snapshot, listOf(impossible),
            mapOf("attack" to "1d20", "damage" to "1d6"))
        val trace = EffectRuntimeHistory.steps(result, listOf(impossible))
        assertFalse(trace.single().activated)
        assertTrue(trace.single().actions.isEmpty())
        assertEquals(24, EffectRuntimeHistory.adjustedLegacyTotal(result, 24))
        assertEquals(4, result.finalSnapshot.parts.getValue("damage").total)
    }

    @Test
    fun subsequentDiceThrowsAppearInDurableTrace() {
        val trigger = bonus.copy(actions = listOf(
            EffectAction("after", EffectActionType.ROLL_AFTER, "damage",
                EffectValueScope.DICE_ONLY, "1d6"),
        ))
        val result = EffectSequenceExecutor.execute(snapshot, listOf(trigger),
            mapOf("attack" to "1d20", "damage" to "1d6"), Random(7))
        val trace = EffectRuntimeHistory.steps(result, listOf(trigger))
        assertEquals(1, result.generatedDice.size)
        assertTrue(trace.single().actions.single().generatedDiceDetail?.contains("d6") == true)
        assertEquals(result.finalSnapshot.parts.getValue("damage").total,
            4 + result.generatedDice.single().result.total)
    }
}
