package com.keyserdsoze.dicethrower.dice

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DiceExpressionTest {
    @Test
    fun parsesCompositeExpression() {
        val result = DiceExpression.parse("4d3 + 3d6 + 10").evaluate(Random(42))
        assertTrue(result.total >= 17)
        assertTrue(result.total <= 40)
        assertEquals(2, result.components.size)
        assertEquals(10, result.constantTotal)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedDice() {
        DiceExpression.parse("1d5+2")
    }

    @Test
    fun supportsD8UsedByGuidedBuilder() {
        val result = DiceExpression.parse("1d8").evaluate(Random(3))
        assertTrue(result.total in 1..8)
    }

    @Test
    fun supportsSubtraction() {
        val result = DiceExpression.parse("1d2-10").evaluate(Random(1))
        assertTrue(result.total in -9..-8)
    }

    @Test
    fun expectedTotalUsesDiceMeansSignsAndConstants() {
        val result = DiceExpression.parse("2d6-1d4+3").evaluate(Random(7))

        assertEquals(7.5, result.expectedTotal(), 0.0001)
    }

    @Test
    fun supportsParenthesesAndScalarMultiplication() {
        val result = DiceExpression.parse("(1d2+2)*3").evaluate(Random(7))

        assertTrue(result.total in 9..12)
        assertEquals(6, result.constantTotal)
        assertEquals(3, result.components.single().sign)
        assertEquals(10.5, result.expectedTotal(), 0.0001)
    }

    @Test
    fun supportsScalarBeforeDiceExpression() {
        val result = DiceExpression.parse("2*(1d6-1)").evaluate(Random(12))

        assertEquals(2, result.components.single().sign)
        assertEquals(-2, result.constantTotal)
        assertEquals(-2, DiceExpression.parse("2*(1d6-1)").constantTotal())
    }

    @Test
    fun multiplicationHasHigherPrecedenceAndParenthesesCanOverrideIt() {
        assertEquals(14, DiceExpression.parse("2+3*4").evaluate(Random(1)).total)
        assertEquals(20, DiceExpression.parse("(2+3)*4").evaluate(Random(1)).total)
    }

    @Test
    fun acceptsReadableMultiplicationSymbolsWithSamePrecedence() {
        listOf("2x3+4", "2×3+4", "2*3+4", "2X3+4").forEach { expression ->
            assertEquals(10, DiceExpression.parse(expression).evaluate(Random(1)).total)
        }
        assertEquals(14, DiceExpression.parse("2+3x4").evaluate(Random(1)).total)
        assertEquals(20, DiceExpression.parse("(2+3)x4").evaluate(Random(1)).total)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDiceToDiceMultiplicationToKeepStatisticsLinear() {
        DiceExpression.parse("1d6*1d8")
    }

    @Test
    fun whitespaceSeparatesTokensButNeverConcatenatesDigits() {
        assertEquals("1d20+5", DiceExpression.parse(" 1d20 +\t5 ").source)
        assertEquals("2d6x3", DiceExpression.parse("2d6 x 3").source)
        listOf("3 1d6", "2 4", "1d6 6", "1d2 0", "(1d4) 2").forEach { expression ->
            assertRejected(expression)
        }
    }

    @Test
    fun onlyAsciiDigitsFormNumbers() {
        // Arabic-Indic and Extended Arabic-Indic (Persian) digits.
        listOf("١d٦", "1d٢٠", "٣", "1d20+٥", "۲d6").forEach { expression ->
            assertRejected(expression)
        }
        assertEquals(7, DiceExpression.parse("3+4").evaluate(Random(1)).total)
    }

    @Test
    fun rejectsIntegerOverflowInsteadOfWrappingAround() {
        listOf(
            "1d6*65536*65536",
            "65536*65536*1d6",
            "2147483647+1",
            "-2147483647-2",
            "2147483648",
            "1d20+2147483647",
            "(1d100+1)*30000000",
        ).forEach { expression ->
            assertRejected(expression)
        }
        assertEquals(Int.MAX_VALUE, DiceExpression.parse("2147483647").evaluate(Random(1)).total)
        assertEquals(2147483646, DiceExpression.parse("2147483647-1").evaluate(Random(1)).total)
        val scaled = DiceExpression.parse("1d6*1000000").evaluate(Random(5))
        assertTrue(scaled.total in 1_000_000..6_000_000)
        assertEquals(scaled.total, scaled.components.sumOf { it.subtotal })
    }

    @Test
    fun expectedTotalIsComputedWithoutIntOverflow() {
        val result = DiceRollResult(
            total = 0,
            components = listOf(DiceComponent(count = 3, sides = 6, sign = 1_000_000_000, rolls = listOf(1, 1, 1))),
            constantTotal = 0,
        )
        assertEquals(10_500_000_000.0, result.expectedTotal(), 0.5)
    }

    @Test
    fun enforcesGlobalSizeLimits() {
        assertRejected("1+".repeat(2048) + "1")
        assertRejected("(".repeat(DiceExpression.MAX_NESTING_DEPTH + 1) + "1" +
            ")".repeat(DiceExpression.MAX_NESTING_DEPTH + 1))
        assertRejected("-".repeat(DiceExpression.MAX_NESTING_DEPTH + 1) + "1")
        // Deep input is rejected with IllegalArgumentException, never a StackOverflowError.
        assertRejected("(".repeat(4000))
        assertRejected("100d6+100d6+100d6+100d6+100d6+1d6")
        assertRejected("1+".repeat(DiceExpression.MAX_TERMS) + "1")

        val nested = "(".repeat(DiceExpression.MAX_NESTING_DEPTH) + "1d6" +
            ")".repeat(DiceExpression.MAX_NESTING_DEPTH)
        assertEquals(1, DiceExpression.parse(nested).diceCount)
        val maximum = DiceExpression.parse("100d6+100d6+100d6+100d6+100d6")
        assertEquals(DiceExpression.MAX_TOTAL_DICE, maximum.diceCount)
        assertEquals(5, maximum.termCount)
        assertEquals(DiceExpression.MAX_TERMS,
            DiceExpression.parse("1+".repeat(DiceExpression.MAX_TERMS - 1) + "1").termCount)
    }

    private fun assertRejected(expression: String) {
        try {
            DiceExpression.parse(expression)
            fail("Accepted invalid expression: $expression")
        } catch (_: IllegalArgumentException) {
            // Expected: invalid user data is reported as an argument error.
        }
    }
}
