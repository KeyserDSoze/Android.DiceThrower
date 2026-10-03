package com.keyserdsoze.dicethrower.dice

import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.LevelRuleKind
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollLevelRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RollFormulaResolverTest {
    private val character = CharacterProfile(
        id = "character",
        name = "Mage",
        level = 4,
    )

    private val modifiers = listOf(
        CharacterModifier(
            id = "int",
            characterId = character.id,
            name = "Intelligenza",
            value = 3,
        ),
    )

    @Test
    fun resolvesNamedModifierAndLevel() {
        val resolved = RollFormulaResolver.resolveTemplate(
            expression = "1d6+{Intelligenza}+{level}",
            level = character.level,
            modifiers = modifiers,
        )

        assertEquals("1d6+3+4", resolved)
    }

    @Test
    fun resolvesModifierNamesCaseInsensitively() {
        val resolved = RollFormulaResolver.resolveTemplate(
            expression = "1d6+{intelligenza}",
            level = character.level,
            modifiers = modifiers,
        )

        assertEquals("1d6+3", resolved)
    }

    @Test
    fun normalizesNegativeModifierSigns() {
        val negative = modifiers.first().copy(value = -2)
        val resolved = RollFormulaResolver.resolveTemplate(
            expression = "1d20+{Intelligenza}",
            level = character.level,
            modifiers = listOf(negative),
        )

        assertEquals("1d20-2", resolved)
    }

    @Test
    fun appliesThresholdAndPeriodicRules() {
        val roll = RollDefinition(
            id = "fireball",
            characterId = character.id,
            name = "Fireball",
            expression = "1d6+{Intelligenza}",
            levelRules = listOf(
                RollLevelRule(
                    id = "threshold",
                    kind = LevelRuleKind.FROM_LEVEL,
                    trigger = 4,
                    expression = "1d6",
                ),
                RollLevelRule(
                    id = "periodic",
                    kind = LevelRuleKind.EVERY_LEVELS,
                    trigger = 2,
                    expression = "1d6+2",
                ),
            ),
        )

        val resolved = RollFormulaResolver.resolve(character, modifiers, roll)

        assertEquals("1d6+3+1d6+1d6+2+1d6+2", resolved.expression)
        assertEquals(2, resolved.appliedRules.size)
    }

    @Test
    fun thresholdDoesNotApplyEarly() {
        val roll = RollDefinition(
            id = "scaled",
            characterId = character.id,
            name = "Scaled",
            expression = "1d6",
            levelRules = listOf(
                RollLevelRule(
                    id = "later",
                    kind = LevelRuleKind.FROM_LEVEL,
                    trigger = 5,
                    expression = "1d6",
                ),
            ),
        )

        val resolved = RollFormulaResolver.resolve(character, modifiers, roll)

        assertEquals("1d6", resolved.expression)
        assertTrue(resolved.appliedRules.isEmpty())
    }

    @Test
    fun rejectsUnknownVariables() {
        assertFalse(
            RollFormulaResolver.validateTemplate(
                expression = "1d6+{Wisdom}",
                level = character.level,
                modifiers = modifiers,
            ),
        )
    }
}
