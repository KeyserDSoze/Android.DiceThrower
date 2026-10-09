package com.keyserdsoze.dicethrower.dice

import com.keyserdsoze.dicethrower.model.EffectAction
import com.keyserdsoze.dicethrower.model.EffectActionType
import com.keyserdsoze.dicethrower.model.EffectValueScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectFormulaInterpreterTest {
    private fun eval(s: String) = EffectFormulaInterpreter.evaluate(
        s,
        variables = mapOf("level" to 20.0, "Strength" to 2.0),
        partTotals = mapOf("damage" to 9.0),
    )

    @Test
    fun floorCeilAndRoundHaveExplicitSemantics() {
        assertEquals(3.0, eval("f(level/7)"), 0.000001)
        assertEquals(3.0, eval("c(level/7)"), 0.000001)
        assertEquals(3.0, eval("r(level/7)"), 0.000001)
        assertEquals(-2.0, eval("f(-1.2)"), 0.000001)
        assertEquals(-1.0, eval("c(-1.2)"), 0.000001)
        assertEquals(2.0, eval("r(1.5)"), 0.000001)
        assertEquals(-2.0, eval("r(-1.5)"), 0.000001)
        assertEquals(1.0, eval("r(0.5)"), 0.000001)
        assertEquals(-1.0, eval("r(-0.5)"), 0.000001)
        assertEquals(2.0, eval("f(level/10)"), 0.000001)
    }

    @Test
    fun arithmeticSupportsPrecedenceAliasesAndStableIds() {
        assertEquals(27.0, eval("(1+f(level/10))*{partId:damage}"), 0.000001)
        assertEquals(4.5, eval("{partId:damage}/2"), 0.000001)
        assertEquals(2.0, eval("{strength}"), 0.000001)
        assertEquals(4.0, eval("-(-Strength)+2"), 0.000001)
        assertEquals(14.0, eval("2+3*4"), 0.000001)
        assertEquals(20.0, eval("c(19.1)"), 0.000001)
    }

    @Test
    fun invalidOrDangerousMathFailsWithoutSamplingDice() {
        for (formula in listOf(
            "1d20", "1/0", "1/(2-2)", "bad(5)",
            "{parts:damage}", "{partId:unknown}", "{unknown}",
            "1+", "c(5", "1e2000", "(",
        )) {
            assertTrue(formula, runCatching { eval(formula) }.isFailure)
        }
        assertTrue(runCatching { eval("f(".repeat(60) + "5" + ")".repeat(60)) }.isFailure)
        assertEquals(3, EffectFormulaInterpreter.toInt(3.8))
        assertEquals(4, EffectFormulaInterpreter.toInt(3.2, EffectResultRounding.CEIL))
        assertEquals(-2, EffectFormulaInterpreter.toInt(-1.5, EffectResultRounding.ROUND))
        assertTrue(runCatching { EffectFormulaInterpreter.toInt(Double.NaN) }.isFailure)
        assertTrue(runCatching { EffectFormulaInterpreter.toInt(3e30) }.isFailure)
    }

    private val source = DiceRollResult(
        total = 20,
        components = listOf(DiceComponent(1, 20, 1, listOf(18))),
        constantTotal = 2,
    )
    private val snapshot = EffectNumericSnapshot(
        parts = mapOf("attack" to EffectPartValue(source)),
        variables = mapOf("level" to 20),
    )

    private fun action(
        kind: EffectActionType,
        scope: EffectValueScope,
        expression: String,
        target: String = "attack",
    ) = EffectAction("action", kind, target, scope, expression)

    @Test
    fun scopedActionsPreserveNaturalDiceAndIndependentModifiers() {
        val total = EffectActionEngine.apply(
            action(EffectActionType.MULTIPLY, EffectValueScope.TOTAL, "2"), snapshot,
        )
        assertTrue(total.applied)
        assertEquals(40, total.snapshot.parts.getValue("attack").total)
        assertEquals(18, total.snapshot.parts.getValue("attack").dice)
        assertEquals(2, total.snapshot.parts.getValue("attack").modifiers)
        assertEquals(20, source.total)

        val diced = EffectActionEngine.apply(
            action(EffectActionType.MULTIPLY, EffectValueScope.DICE_ONLY, "2"), snapshot,
        )
        assertEquals(38, diced.snapshot.parts.getValue("attack").total)
        assertEquals(36, diced.snapshot.parts.getValue("attack").dice)
        assertEquals(2, diced.snapshot.parts.getValue("attack").modifiers)

        val modifiers = EffectActionEngine.apply(
            action(EffectActionType.ADD, EffectValueScope.MODIFIERS_ONLY, "3"), snapshot,
        )
        assertEquals(23, modifiers.snapshot.parts.getValue("attack").total)
        assertEquals(5, modifiers.snapshot.parts.getValue("attack").modifiers)
    }

    @Test
    fun chainedNumericActionsReadCurrentResultsAndDoNotMutateOriginal() {
        val first = EffectActionEngine.apply(
            action(EffectActionType.MULTIPLY, EffectValueScope.TOTAL, "2"), snapshot)
        val second = EffectActionEngine.apply(
            action(EffectActionType.ADD, EffectValueScope.TOTAL, "5"), first.snapshot)
        assertEquals(45, second.snapshot.parts.getValue("attack").total)
        assertEquals(20, snapshot.parts.getValue("attack").total)
        val scaled = EffectActionEngine.apply(action(
            EffectActionType.REPLACE, EffectValueScope.TOTAL,
            "{partId:attack}*(1+f(level/10))",
        ), snapshot)
        assertEquals(60, scaled.snapshot.parts.getValue("attack").total)
    }

    @Test
    fun invalidActionsFailClosedAndRetainPreviousSnapshot() {
        for (test in listOf(
            action(EffectActionType.ADD, EffectValueScope.TOTAL, "1/0"),
            action(EffectActionType.REROLL, EffectValueScope.TOTAL, ""),
            action(EffectActionType.ADD, EffectValueScope.TOTAL, "3", target = "missing"),
        )) {
            val failed = EffectActionEngine.apply(test, snapshot)
            assertFalse(failed.applied)
            assertEquals(snapshot, failed.snapshot)
            assertTrue(failed.error?.isNotBlank() == true)
        }
    }
}
