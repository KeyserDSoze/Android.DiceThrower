package com.keyserdsoze.dicethrower.ui.v2

import com.keyserdsoze.dicethrower.dice.DiceExpression
import com.keyserdsoze.dicethrower.dice.RollFormulaResolver
import com.keyserdsoze.dicethrower.model.CharacterModifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormulaComposerTest {
    @Test
    fun parsesSimpleTermsAndKeepsSigns() {
        val terms = requireNotNull(FormulaComposer.parse("1d20-2d6+3", 1, emptyList()))

        assertEquals(3, terms.size)
        assertEquals(listOf('+', '-', '+'), terms.map { it.sign })
        assertEquals(listOf("1d20", "2d6", "3"), terms.map { it.expression })
        assertEquals("1d20-2d6+3", FormulaComposer.serialize(terms))
    }

    @Test
    fun groupingNeighboringTermsKeepsResultsExactlyEquivalent() {
        val source = "1d20-2d6+3"
        val terms = requireNotNull(FormulaComposer.parse(source, 1, emptyList()))
        assertFalse(FormulaComposer.canGroup(terms, setOf(0, 2)))
        assertTrue(FormulaComposer.canGroup(terms, setOf(1, 2)))

        val grouped = FormulaComposer.group(terms, setOf(1, 2))
        val expression = FormulaComposer.serialize(grouped)
        assertEquals("1d20-(2d6-3)", expression)
        assertEquals(
            DiceExpression.parse(source).diceShape(),
            DiceExpression.parse(expression).diceShape(),
        )
        assertEquals(
            DiceExpression.parse(source).constantTotal(),
            DiceExpression.parse(expression).constantTotal(),
        )
        assertEquals(source, FormulaComposer.serialize(FormulaComposer.ungroup(grouped, 1)))
    }

    @Test
    fun groupMultiplierAcceptsVariablesAndUngroupPreservesEvaluation() {
        val modifier = CharacterModifier("attack", "character", "Attack", 3)
        val modifiers = listOf(modifier)
        val terms = requireNotNull(FormulaComposer.parse("1d20+2", 4, modifiers))
        val grouped = FormulaComposer.group(terms, setOf(0, 1))
        val multiplied = FormulaComposer.multiplyGroup(grouped, 0, "{level}")
        assertEquals("(1d20+2)x{level}", FormulaComposer.serialize(multiplied))

        val flattened = FormulaComposer.ungroup(multiplied, 0)
        val before = RollFormulaResolver.resolveTemplate(FormulaComposer.serialize(multiplied), 4, modifiers)
        val after = RollFormulaResolver.resolveTemplate(FormulaComposer.serialize(flattened), 4, modifiers)
        assertEquals(DiceExpression.parse(before).diceShape(), DiceExpression.parse(after).diceShape())
        assertEquals(DiceExpression.parse(before).constantTotal(), DiceExpression.parse(after).constantTotal())
        assertNotNull(FormulaComposer.parse(FormulaComposer.serialize(flattened), 4, modifiers))
    }

    @Test
    fun importsPrefixAndSuffixGroupMultipliersWithoutNesting() {
        val source = "2*(1d20+1)"
        val imported = requireNotNull(FormulaComposer.parse(source, 1, emptyList()))
        assertTrue(imported.single().isGroup)
        assertEquals("(1d20+1)x2", FormulaComposer.serialize(imported))
        assertFalse(FormulaComposer.canGroup(imported, setOf(0)))
        val nested = requireNotNull(FormulaComposer.parse("((1d20+1))x2", 1, emptyList()))
        assertFalse(nested.single().isGroup)
    }

    @Test
    fun reorderAndDeleteKeepTermSignsAndValidExpression() {
        val original = requireNotNull(FormulaComposer.parse("1d20-1d6+2", 1, emptyList()))
        val moved = FormulaComposer.move(original, 1, -1)
        assertEquals("-1d6+1d20+2", FormulaComposer.serialize(moved))
        assertEquals("-1d6+2", FormulaComposer.serialize(FormulaComposer.remove(moved, 1)))
        assertEquals(original, FormulaComposer.move(original, 0, -1))
    }

    @Test
    fun invalidTemplatesAreNotProjectedIntoComposer() {
        assertNull(FormulaComposer.parse("1d20+(", 1, emptyList()))
        assertNull(FormulaComposer.parse("1d20+{missing}", 1, emptyList()))
    }
}
