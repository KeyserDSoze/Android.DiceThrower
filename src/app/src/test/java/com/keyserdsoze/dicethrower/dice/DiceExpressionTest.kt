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
        DiceExpression.parse("1d8+2")
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
}
