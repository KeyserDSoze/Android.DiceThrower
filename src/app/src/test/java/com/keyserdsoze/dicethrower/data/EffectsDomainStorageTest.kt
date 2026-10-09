package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.AppSettings
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.EffectAction
import com.keyserdsoze.dicethrower.model.EffectActionType
import com.keyserdsoze.dicethrower.model.EffectActivationGroup
import com.keyserdsoze.dicethrower.model.EffectComparison
import com.keyserdsoze.dicethrower.model.EffectCondition
import com.keyserdsoze.dicethrower.model.EffectType
import com.keyserdsoze.dicethrower.model.EffectValueScope
import com.keyserdsoze.dicethrower.model.EffectValueSource
import com.keyserdsoze.dicethrower.model.PartReferenceAliases
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollEffect
import com.keyserdsoze.dicethrower.model.RollSubgroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectsDomainStorageTest {
    private val parts = listOf(
        RollSubgroup("hit-id", "Attack", "1d20+2"),
        RollSubgroup("damage-id", "Damage", "2d6+3"),
    )
    private val effect = RollEffect(
        id = "critical-id", name = "Powerful hit", type = EffectType.BONUS, order = 0,
        stopFollowingEffects = true,
        activationGroups = listOf(
            EffectActivationGroup("or-1", listOf(
                EffectCondition(
                    "natural-20", source = EffectValueSource.PART, partId = "hit-id",
                    scope = EffectValueScope.DICE_ONLY,
                    comparison = EffectComparison.GREATER_OR_EQUAL, threshold = "20",
                ),
                EffectCondition(
                    "level", source = EffectValueSource.VARIABLE, variableName = "level",
                    scope = EffectValueScope.TOTAL,
                    comparison = EffectComparison.GREATER_OR_EQUAL, threshold = "10",
                ),
            )),
            EffectActivationGroup("or-2", listOf(
                EffectCondition("range", partId = "hit-id",
                    comparison = EffectComparison.LESS_OR_EQUAL, threshold = "3"),
            )),
        ),
        actions = listOf(
            EffectAction(
                "double-damage", EffectActionType.MULTIPLY,
                targetPartId = "damage-id", scope = EffectValueScope.DICE_ONLY,
                expression = "1+f({level}/10)*{partId:damage-id}",
            ),
            EffectAction("extra", EffectActionType.ROLL_AFTER,
                targetPartId = "damage-id", scope = EffectValueScope.TOTAL),
        ),
    )
    private val roll = RollDefinition(
        id = "r", characterId = "character", name = "Attack",
        expression = "(1d20+2)+(2d6+3)", subgroups = parts, effects = listOf(effect),
    )
    private val data = AppData(
        characters = listOf(CharacterProfile(id = "character", name = "Hero")),
        rolls = listOf(roll),
    )

    @Test
    fun effectGraphSurvivesLocalBackupAndCloudCodec() {
        assertTrue(AppDataValidator.validate(data).isEmpty())
        val restored = AppBackupCodec.decode(AppBackupCodec.encode(data, AppSettings(), "it"))
        assertEquals(data, restored.data)
        assertEquals(data.rolls.single().effects,
            AppDataJsonCodec.decodeData(AppDataJsonCodec.encodeDataForSync(data)).rolls.single().effects)
    }

    @Test
    fun legacyRollsWithoutEffectsLoadWithEmptyList() {
        val json = AppDataJsonCodec.encodeData(data)
        json.getJSONArray("rolls").getJSONObject(0).remove("effects")
        assertTrue(AppDataJsonCodec.decodeData(json).rolls.single().effects.isEmpty())
    }

    @Test
    fun renamedPartsPreserveStoredReferencesAndChangeOnlyReadableProjection() {
        val stable = PartReferenceAliases.store("2*{parts:Damage}+1", parts)
        assertEquals("2*{partId:damage-id}+1", stable)
        assertEquals("2*{parts:Damage}+1", PartReferenceAliases.display(stable, parts))
        val renamed = parts.map { if (it.id == "damage-id") it.copy(name = "Fire") else it }
        assertEquals("2*{parts:Fire}+1", PartReferenceAliases.display(stable, renamed))
        assertEquals(setOf("damage-id"), PartReferenceAliases.referencedPartIds(stable))
        assertEquals(stable, PartReferenceAliases.store(stable, renamed))
        val changed = data.copy(rolls = listOf(roll.copy(subgroups = renamed)))
        assertTrue(AppDataValidator.validate(changed).isEmpty())
    }

    @Test
    fun duplicateAliasesAreRejectedAndAmbiguousDisplayUsesStableId() {
        val duplicates = parts.map { it.copy(name = "Same") }
        assertTrue(runCatching { PartReferenceAliases.store("{parts:Same}", duplicates) }.isFailure)
        assertEquals("{partId:damage-id}", PartReferenceAliases.display("{partId:damage-id}", duplicates))
        assertTrue(runCatching { PartReferenceAliases.store("{parts:missing}", parts) }.isFailure)
    }

    @Test
    fun missingOrDuplicateIdsAndBrokenConditionsAreRejected() {
        val withoutDamage = data.copy(rolls = listOf(roll.copy(subgroups = listOf(parts.first()),
            expression = "(1d20+2)")))
        assertTrue(AppDataValidator.validate(withoutDamage).any { it.contains("missing Part") })
        val duplicate = roll.copy(effects = listOf(effect, effect.copy(name = "Duplicate", order = 1)))
        assertTrue(AppDataValidator.validate(data.copy(rolls = listOf(duplicate))).any {
            it.contains("Duplicate effect ids")
        })
        val emptyGroup = effect.copy(activationGroups = listOf(EffectActivationGroup("empty")))
        assertTrue(AppDataValidator.validate(data.copy(rolls = listOf(roll.copy(effects = listOf(emptyGroup))))).any {
            it.contains("at least one condition")
        })
    }

    @Test
    fun syncRevisionTracksEffectsButKeepsLegacyDefaults() {
        val empty = data.copy(rolls = listOf(roll.copy(effects = emptyList())))
        val oldHash = CharacterRevision.revision(empty, "character")
        assertNotEquals(oldHash, CharacterRevision.revision(data, "character"))
        assertFalse(oldHash.isBlank())
        val renamed = data.copy(rolls = listOf(roll.copy(subgroups = parts.map {
            if (it.id == "damage-id") it.copy(name = "Fire") else it
        })))
        assertNotEquals(CharacterRevision.revision(data, "character"),
            CharacterRevision.revision(renamed, "character"))
    }
}
