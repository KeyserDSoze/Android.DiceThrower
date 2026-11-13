package com.keyserdsoze.dicethrower.dice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceVisualProjectionTest {
    private fun component(sides: Int, vararg values: Int) = DiceComponent(values.size, sides, 1, values.toList())

    private fun result(vararg components: DiceComponent) = DiceRollResult(
        total = components.sumOf { it.subtotal } + 3,
        components = components.toList(),
        constantTotal = 3,
    )

    @Test
    fun everyPercentileValueExpandsWithoutChangingTheLogicalResult() {
        for (value in 1..100) {
            val original = result(component(100, value))
            val detail = original.detail()
            val projection = DiceVisualProjection.project(original)
            assertEquals(2, projection.faces.size)
            val tens = projection.faces[0]
            val units = projection.faces[1]
            assertEquals(100, tens.sides)
            assertEquals(value, tens.value)
            assertFalse(tens.percentileUnits)
            assertEquals(10, units.sides)
            assertEquals(value % 10 + 1, units.value)
            assertTrue(units.percentileUnits)
            assertEquals(tens.componentIndex, units.componentIndex)
            assertEquals(tens.rollIndex, units.rollIndex)
            assertEquals(tens.logicalDieIndex, units.logicalDieIndex)
            assertEquals(0, projection.persistentDiceCount)
            assertEquals(value + 3, original.total)
            assertEquals(listOf(value), original.components.single().rolls)
            assertEquals(detail, original.detail())
        }
    }

    @Test
    fun standaloneD10StillShowsTheSampledOneThroughTen() {
        val projection = DiceVisualProjection.project(result(component(10, *IntArray(10) { it + 1 })))
        assertEquals((1..10).toList(), projection.faces.map { it.value })
        assertTrue(projection.faces.all { it.sides == 10 && !it.percentileUnits })
    }

    @Test
    fun mixedDiceRetainLogicalSlotsForStylesLanesAndEffectAccents() {
        val original = result(component(6, 2, 5), component(100, 10, 100), component(10, 10))
        val projection = DiceVisualProjection.project(original, persistentLogicalDiceCount = 3)
        assertEquals(listOf(0, 0, 1, 1, 1, 1, 2), projection.faces.map { it.componentIndex })
        assertEquals(listOf(0, 1, 0, 0, 1, 1, 0), projection.faces.map { it.rollIndex })
        assertEquals(listOf(0, 1, 2, 2, 3, 3, 4), projection.faces.map { it.logicalDieIndex })
        assertEquals(4, projection.persistentDiceCount)
        assertEquals(6, DiceVisualProjection.project(original, persistentLogicalDiceCount = 4).persistentDiceCount)
        assertEquals(7, DiceVisualProjection.diceCount(original.components))
    }

    @Test
    fun bothPercentileBodiesRemainStillWhenAnEffectAppendsOneDie() {
        val projection = DiceVisualProjection.project(result(component(100, 100), component(4, 3)), 1)
        assertEquals(3, projection.faces.size)
        assertEquals(2, projection.persistentDiceCount)
        assertEquals(listOf(100, 10, 4), projection.faces.map { it.sides })
    }

    @Test
    fun sixPercentilePairsFillTheTableAndASeventhDoesNotSplitTheLimit() {
        val original = result(component(100, 1, 10, 37, 65, 99, 100, 50))
        val projection = DiceVisualProjection.project(original, 7)
        assertEquals(12, projection.faces.size)
        assertEquals(12, projection.persistentDiceCount)
        assertEquals((0..5).flatMap { listOf(it, it) }, projection.faces.map { it.logicalDieIndex })
        assertEquals(7, original.components.single().rolls.size)
        assertEquals(14, DiceVisualProjection.diceCount(original.components))
    }

    @Test
    fun oneRemainingSpaceCannotShowHalfAPercentileOrSkipAheadToLaterDice() {
        val original = result(component(6, *IntArray(11) { 1 }), component(100, 100), component(4, 3))
        val projection = DiceVisualProjection.project(original, 99)
        assertEquals(11, projection.faces.size)
        assertEquals(11, projection.persistentDiceCount)
        assertTrue(projection.faces.all { it.componentIndex == 0 })
    }

    @Test
    fun twoRemainingSpacesCanShowACompletePercentilePair() {
        val projection = DiceVisualProjection.project(result(component(6, *IntArray(10) { 1 }), component(100, 100)))
        assertEquals(12, projection.faces.size)
        assertEquals(listOf(100, 10), projection.faces.takeLast(2).map { it.sides })
        assertTrue(projection.faces.last().percentileUnits)
    }

    @Test
    fun unsupportedLegacyComponentsDoNotShiftLogicalPersistenceOrAppearanceSlots() {
        val original = result(component(5, 2), component(100, 70), component(6, 4))
        val projection = DiceVisualProjection.project(original, 2)
        assertEquals(listOf(1, 1, 2), projection.faces.map { it.componentIndex })
        assertEquals(listOf(1, 1, 2), projection.faces.map { it.logicalDieIndex })
        assertEquals(2, projection.persistentDiceCount)
        assertEquals(3, DiceVisualProjection.diceCount(original.components))
    }

    @Test
    fun capacityUsesActualSamplesRatherThanADeclaredLegacyCount() {
        val original = result(component(100, 10, 20).copy(count = 99))
        assertEquals(4, DiceVisualProjection.project(original).faces.size)
        assertEquals(4, DiceVisualProjection.diceCount(original.components))
    }

    @Test
    fun constantsAndNegativePersistenceCountsDoNotCreateOrRetainDice() {
        assertTrue(DiceVisualProjection.project(result()).faces.isEmpty())
        assertEquals(0, DiceVisualProjection.diceCount(emptyList()))
        assertEquals(0, DiceVisualProjection.project(result(component(100, 10)), -1).persistentDiceCount)
    }
}
