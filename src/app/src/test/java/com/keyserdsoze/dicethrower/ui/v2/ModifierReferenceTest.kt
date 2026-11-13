package com.keyserdsoze.dicethrower.ui.v2

import com.keyserdsoze.dicethrower.model.EffectAction
import com.keyserdsoze.dicethrower.model.EffectActionType
import com.keyserdsoze.dicethrower.model.EffectActivationGroup
import com.keyserdsoze.dicethrower.model.EffectCondition
import com.keyserdsoze.dicethrower.model.EffectType
import com.keyserdsoze.dicethrower.model.EffectValueScope
import com.keyserdsoze.dicethrower.model.EffectValueSource
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollEffect
import com.keyserdsoze.dicethrower.model.RollLevelRule
import com.keyserdsoze.dicethrower.model.LevelRuleKind
import com.keyserdsoze.dicethrower.model.RollSubgroup
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModifierReferenceTest {
    private val plain = RollDefinition(id = "roll", characterId = "hero", name = "Attack", expression = "1d20+2")

    private fun withEffect(condition: EffectCondition? = null, action: EffectAction? = null) = plain.copy(
        effects = listOf(RollEffect(
            id = "effect", name = "Effect", type = EffectType.BONUS,
            activationGroups = listOf(EffectActivationGroup("group", listOfNotNull(
                condition ?: EffectCondition("always", partId = "attack", threshold = "1"),
            ))),
            actions = listOf(action ?: EffectAction("act", EffectActionType.ADD, "attack", expression = "1")),
        )),
    )

    @Test
    fun expressionsPartsAndLevelRulesReferenceModifiers() {
        assertFalse(modifierIsReferenced("Strength", listOf(plain)))
        assertTrue(modifierIsReferenced("Strength", listOf(plain.copy(expression = "1d20+{ strength }"))))
        val supportOnly = plain.copy(subgroups = listOf(
            RollSubgroup("attack", "Attack", "1d20+2"),
            RollSubgroup("bonus", "Bonus", "1d6+{Strength}", includeInNormalRoll = false),
        ))
        assertTrue(modifierIsReferenced("Strength", listOf(supportOnly)))
        val rule = plain.copy(levelRules = listOf(
            RollLevelRule("rule", LevelRuleKind.FROM_LEVEL, trigger = 2, expression = "{Strength}"),
        ))
        assertTrue(modifierIsReferenced("Strength", listOf(rule)))
    }

    @Test
    fun effectConditionsAndActionsReferenceModifiers() {
        assertFalse(modifierIsReferenced("Strength", listOf(withEffect())))
        assertTrue(modifierIsReferenced("Strength", listOf(withEffect(
            condition = EffectCondition("variable", source = EffectValueSource.VARIABLE,
                variableName = "strength", scope = EffectValueScope.TOTAL, threshold = "3"),
        ))))
        assertTrue(modifierIsReferenced("Strength", listOf(withEffect(
            condition = EffectCondition("threshold", partId = "attack", threshold = "10+{Strength}"),
        ))))
        assertTrue(modifierIsReferenced("Strength", listOf(withEffect(
            condition = EffectCondition("bare", partId = "attack", threshold = "f(Strength/2)"),
        ))))
        assertTrue(modifierIsReferenced("Strength", listOf(withEffect(
            action = EffectAction("add", EffectActionType.ADD, "attack", expression = "Strength*2"),
        ))))
        assertTrue(modifierIsReferenced("Strength", listOf(withEffect(
            action = EffectAction("again", EffectActionType.ROLL_AFTER, "attack", expression = "1d6+{Strength}"),
        ))))
    }

    @Test
    fun functionsPartIdsAndLongerNamesAreNotModifierReferences() {
        val effect = withEffect(
            condition = EffectCondition("c", partId = "attack", threshold = "f({partId:f}) + r (2) + Strengthy"),
            action = EffectAction("a", EffectActionType.ADD, "attack", expression = "{partId:r}+c(1.5)"),
        )
        listOf("f", "r", "c", "Strength", "partId").forEach { name ->
            assertFalse("$name is not referenced", modifierIsReferenced(name, listOf(effect)))
        }
        assertTrue(modifierIsReferenced("Strengthy", listOf(effect)))
    }
}
