package com.keyserdsoze.dicethrower.dice

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDiceToDiceMultiplicationToKeepStatisticsLinear() {
        DiceExpression.parse("1d6*1d8")
    }
}
