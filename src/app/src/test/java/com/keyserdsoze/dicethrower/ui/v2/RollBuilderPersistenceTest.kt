package com.keyserdsoze.dicethrower.ui.v2

import com.keyserdsoze.dicethrower.data.AppDataValidator
import com.keyserdsoze.dicethrower.dice.DiceExpression
import com.keyserdsoze.dicethrower.dice.RollFormulaResolver
import com.keyserdsoze.dicethrower.dice.subgroupResults
import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollSubgroup
import com.keyserdsoze.dicethrower.model.RollSubgroupOperator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RollBuilderPersistenceTest {
    private val character = CharacterProfile(id = "hero", name = "Hero", level = 3)

    private fun roll(parts: List<RollSubgroup>) = RollDefinition(
        id = "roll",
        characterId = character.id,
        name = "Attack",
        // This is the same canonicalization used by RollBuilderScreenV2's Save action.
        expression = RollFormulaResolver.canonicalExpression(parts),
        subgroups = parts,
    )

    @Test
    fun onePartManualEditAlwaysPersistsCanonicalExpression() {
        val part = RollSubgroup(id = "part", expression = "1d20+3d6+20d10")
        val saved = roll(listOf(part))
        assertEquals("(1d20+3d6+20d10)", saved.expression)
        assertTrue(AppDataValidator.validate(AppData(characters = listOf(character), rolls = listOf(saved))).isEmpty())

        // Regression: the former editor saved raw text for a single part, crashing
        // when AppDataStore's validator rejected a non-canonical expression.
        val oldSave = saved.copy(expression = part.expression)
        assertTrue(
            AppDataValidator.validate(AppData(characters = listOf(character), rolls = listOf(oldSave)))
                .any { it.contains("subgroup formula") },
        )
    }

    @Test
    fun multiplePartsKeepNamesIdsOperatorsAndSeparateResults() {
        val parts = listOf(
            RollSubgroup(id = "attack", name = "Attack", expression = "1d20+{level}"),
            RollSubgroup(id = "damage", name = "Damage", expression = "3d6+2", operator = RollSubgroupOperator.ADD),
        )
        val saved = roll(parts)
        assertEquals("(1d20+{level})+(3d6+2)", saved.expression)
        assertEquals(parts, saved.subgroups)
        assertTrue(AppDataValidator.validate(AppData(characters = listOf(character), rolls = listOf(saved))).isEmpty())

        val resolved = RollFormulaResolver.resolve(character, emptyList(), saved)
        val outcome = DiceExpression.parse(resolved.expression).evaluate(Random(17))
        val results = resolved.subgroupResults(outcome)
        assertEquals(listOf("Attack", "Damage"), results.map { it.subgroup.name })
        assertEquals(2, results.size)
        assertEquals(1, results[0].result.components.size)
        assertEquals(1, results[1].result.components.size)
    }

    @Test
    fun invalidManualFormulaCannotBeProjectedIntoComposerOrSaved() {
        assertFalse(RollFormulaResolver.validateTemplate("1d20+(", character.level, emptyList()))
        assertTrue(FormulaComposer.parse("1d20+(", character.level, emptyList()) == null)
        val valid = "1d20+3d6+20d10"
        val terms = requireNotNull(FormulaComposer.parse(valid, character.level, emptyList()))
        assertEquals(3, terms.size)
        assertEquals(valid, FormulaComposer.serialize(terms))
    }
}
