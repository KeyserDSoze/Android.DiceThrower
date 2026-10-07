package com.keyserdsoze.dicethrower.dice

import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.LevelRuleKind
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollLevelRule
import com.keyserdsoze.dicethrower.model.RollSubgroup
import com.keyserdsoze.dicethrower.model.RollSubgroupOperator
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
    fun explicitAndLegacyAdjacentMultiplicationDoNotConcatenateDigits() {
        listOf("2x{level}", "2×{level}", "2*{level}", "2{level}").forEach { formula ->
            val resolved = RollFormulaResolver.resolveTemplate(formula, character.level, modifiers)
            assertEquals(8, DiceExpression.parse(resolved).evaluate(kotlin.random.Random(1)).total)
        }
        assertEquals("2x4", RollFormulaResolver.resolveTemplate("2{level}", character.level, modifiers))
        assertEquals("2x4", RollFormulaResolver.resolveTemplate("2x{level}", character.level, modifiers))
    }

    @Test
    fun levelCanDriveDiceCount() {
        val resolved = RollFormulaResolver.resolveTemplate(
            expression = "{level}d6",
            level = character.level,
            modifiers = modifiers,
        )

        assertEquals("4d6", resolved)
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
    fun periodicRulesCanReduceTheRoll() {
        val roll = RollDefinition(
            id = "fatigue",
            characterId = character.id,
            name = "Fatigue",
            expression = "3d6",
            levelRules = listOf(
                RollLevelRule(
                    id = "penalty",
                    kind = LevelRuleKind.EVERY_LEVELS,
                    trigger = 2,
                    expression = "-1d4",
                ),
            ),
        )

        val resolved = RollFormulaResolver.resolve(character, modifiers, roll)

        assertEquals("3d6-1d4-1d4", resolved.expression)
        assertEquals(listOf(roll.levelRules.single()), resolved.appliedRules)
    }

    @Test
    fun resolvesSubgroupsIntoCanonicalFormula() {
        val roll = RollDefinition(
            id = "attack-damage",
            characterId = character.id,
            name = "Attack + damage",
            expression = "(1d20+{Intelligenza})+(2d6)",
            subgroups = listOf(
                RollSubgroup("attack", "Attack", "1d20+{Intelligenza}"),
                RollSubgroup("damage", "Damage", "2d6"),
            ),
        )

        val resolved = RollFormulaResolver.resolve(character, modifiers, roll)

        assertEquals("(1d20+3)+(2d6)", resolved.expression)
        assertEquals(listOf("1d20+3", "2d6"), resolved.subgroups.map { it.expression })
    }

    @Test
    fun subgroupResultsPreserveAggregateSigns() {
        val roll = RollDefinition(
            id = "contest",
            characterId = character.id,
            name = "Contest",
            expression = "(1d20+2)-(1d6)",
            subgroups = listOf(
                RollSubgroup("attack", "Attack", "1d20+2"),
                RollSubgroup("penalty", "Penalty", "1d6", RollSubgroupOperator.SUBTRACT),
            ),
        )
        val resolved = RollFormulaResolver.resolve(character, modifiers, roll)
        val outcome = DiceExpression.parse(resolved.expression).evaluate(kotlin.random.Random(42))

        val grouped = resolved.subgroupResults(outcome)

        assertEquals(2, grouped.size)
        assertEquals(outcome.total, grouped.sumOf { it.result.total })
        assertTrue(grouped[1].result.total < 0)
    }

    @Test
    fun subgroupResultsSupportLeadingNegativeGroupAndScalarMultiplication() {
        val roll = RollDefinition(
            id = "scaled-contest",
            characterId = character.id,
            name = "Scaled contest",
            expression = "-((1d6+2)*2)+(1d4)",
            subgroups = listOf(
                RollSubgroup(
                    "penalty",
                    "Penalty",
                    "(1d6+2)*2",
                    RollSubgroupOperator.SUBTRACT,
                ),
                RollSubgroup("recovery", "Recovery", "1d4"),
            ),
        )
        val resolved = RollFormulaResolver.resolve(character, modifiers, roll)
        val outcome = DiceExpression.parse(resolved.expression).evaluate(kotlin.random.Random(7))

        val grouped = resolved.subgroupResults(outcome)

        assertEquals("-((1d6+2)*2)+(1d4)", resolved.expression)
        assertEquals(outcome.total, grouped.sumOf { it.result.total })
        assertTrue(grouped.first().result.total < 0)
        assertTrue(grouped.last().result.total > 0)
    }

    @Test
    fun subgroupComponentMappingIsStableForAppearanceOverrides() {
        val roll = RollDefinition(
            id = "combo",
            characterId = character.id,
            name = "Combo",
            expression = "(2d6+1d8)+(1d20)",
            subgroups = listOf(
                RollSubgroup("damage", "Damage", "2d6+1d8"),
                RollSubgroup("attack", "Attack", "1d20"),
            ),
        )

        val resolved = RollFormulaResolver.resolve(character, modifiers, roll)

        assertEquals(
            mapOf(0 to "damage", 1 to "damage", 2 to "attack"),
            resolved.subgroupIdByComponentIndex(),
        )
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
