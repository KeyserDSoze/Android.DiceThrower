package com.keyserdsoze.dicethrower.ui.v2

import com.keyserdsoze.dicethrower.model.EffectAction
import com.keyserdsoze.dicethrower.model.EffectActionType
import com.keyserdsoze.dicethrower.model.EffectActivationGroup
import com.keyserdsoze.dicethrower.model.EffectCondition
import com.keyserdsoze.dicethrower.model.EffectType
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollEffect
import com.keyserdsoze.dicethrower.model.RollSubgroup
import com.keyserdsoze.dicethrower.model.RollSubgroupOperator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RollBuilderDraftTest {
    @Test
    fun newRollKeepsItsPartIdAndDefaultOptionsAfterRestoration() {
        var generated = 0
        val draft = RollBuilderDraft.create(null, "spells") { "part-${++generated}" }
        val restored = RollBuilderDraftCodec.decode(RollBuilderDraftCodec.encode(draft))
        assertEquals(draft, restored)
        assertEquals(1, generated)
        assertEquals("part-1", restored.subgroups.single().id)
        assertEquals("spells", restored.groupId)
        assertTrue(restored.doubleRollEnabled)
        assertTrue(restored.subgroups.single().includeInDoubleRoll)
    }

    @Test
    fun incompleteTextAndEffectsRoundTripWithoutValidationOrNormalization() {
        val parts = listOf(
            RollSubgroup("attack", "  Attack  ", "1d20+{"),
            RollSubgroup("damage", "Damage", "", RollSubgroupOperator.SUBTRACT, true, false),
        )
        val effect = RollEffect(
            id = "critical", name = "", type = EffectType.MALUS, enabled = false,
            stopFollowingEffects = true,
            activationGroups = listOf(
                EffectActivationGroup("or-1", listOf(EffectCondition("condition", partId = "attack", threshold = "f("))),
                EffectActivationGroup("or-2"),
            ),
            actions = listOf(EffectAction("action", EffectActionType.ROLL_AFTER, "damage", expression = "{parts:Damage")),
        )
        val draft = RollBuilderDraft("Incomplete \"spell\"\n", null, false, "", parts, listOf(effect))
        assertEquals(draft, RollBuilderDraftCodec.decode(RollBuilderDraftCodec.encode(draft)))
    }

    @Test
    fun existingPartAndEffectIdsAreNeverRegenerated() {
        val part = RollSubgroup("saved-part", "Damage", "2d6")
        val effect = RollEffect("saved-effect", "Bonus", EffectType.BONUS)
        val existing = RollDefinition(
            "roll", "hero", "Sword", "(2d6)", "weapons", subgroups = listOf(part),
            effects = listOf(effect), minimumLevel = 6,
        )
        val draft = RollBuilderDraft.create(existing, "different-group") { error("Must not allocate another Part") }
        assertEquals("weapons", draft.groupId)
        assertEquals("6", draft.minimumLevelText)
        assertFalse(draft.doubleRollEnabled)
        assertEquals(listOf(part), draft.subgroups)
        assertEquals(listOf(effect), draft.effects)
    }

    @Test
    fun legacyExpressionIsPreservedAsOnePart() {
        val legacy = RollDefinition("roll", "hero", "Legacy", "1d8+3")
        val draft = RollBuilderDraft.create(legacy, null) { "legacy-part" }
        assertEquals("1d8+3", draft.subgroups.single().expression)
        assertFalse(draft.subgroups.single().includeInDoubleRoll)
        assertEquals(draft, RollBuilderDraftCodec.decode(RollBuilderDraftCodec.encode(draft)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedEditorFormatIsRejected() {
        RollBuilderDraftCodec.decode("""{"version":2}""")
    }
}
