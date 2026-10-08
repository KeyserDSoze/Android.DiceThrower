package com.keyserdsoze.dicethrower.ui.v2

import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.RollSubgroup
import com.keyserdsoze.dicethrower.model.RollSubgroupOperator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RollBuilderStateTest {
    @Test
    fun moveRollSubgroupReordersWithoutChangingContent() {
        val groups = listOf(
            RollSubgroup("attack", "Attack", "1d20"),
            RollSubgroup("damage", "Damage", "2d6"),
            RollSubgroup("bonus", "Bonus", "1d4"),
        )

        val moved = moveRollSubgroup(groups, index = 0, offset = 1)

        assertEquals(listOf("damage", "attack", "bonus"), moved.map { it.id })
        assertEquals(groups.toSet(), moved.toSet())
    }

    @Test
    fun moveRollSubgroupIgnoresOutOfBoundsMove() {
        val groups = listOf(RollSubgroup("attack", "Attack", "1d20"))

        assertEquals(groups, moveRollSubgroup(groups, index = 0, offset = -1))
        assertEquals(groups, moveRollSubgroup(groups, index = 0, offset = 1))
    }

    @Test
    fun unchangedAdvancedCanonicalExpressionPreservesNamedSubgroups() {
        val groups = listOf(
            RollSubgroup("attack", "Attack", "1d20+2"),
            RollSubgroup("damage", "Damage", "2d6", RollSubgroupOperator.SUBTRACT),
        )

        val imported = guidedSubgroupsFromAdvancedExpression(
            expression = "(1d20+2)-(2d6)",
            current = groups,
            level = 3,
            modifiers = emptyList(),
        )

        assertEquals(groups, imported)
    }

    @Test
    fun changedValidAdvancedExpressionBecomesLosslessSingleGuidedGroup() {
        val modifier = CharacterModifier("str", "hero", "Strength", 3)
        val imported = guidedSubgroupsFromAdvancedExpression(
            expression = " 2*(1d20+{Strength}) ",
            current = listOf(RollSubgroup("attack", "Attack", "1d20")),
            level = 5,
            modifiers = listOf(modifier),
            idFactory = { "new" },
        )

        assertEquals(1, imported?.size)
        assertEquals("attack", imported?.single()?.id)
        assertEquals("Attack", imported?.single()?.name)
        assertEquals("2*(1d20+{Strength})", imported?.single()?.expression)
        assertEquals(RollSubgroupOperator.ADD, imported?.single()?.operator)
    }

    @Test
    fun invalidAdvancedExpressionIsNotImported() {
        val imported = guidedSubgroupsFromAdvancedExpression(
            expression = "1d20+(",
            current = listOf(RollSubgroup("attack", "Attack", "1d20")),
            level = 1,
            modifiers = emptyList(),
        )

        assertNull(imported)
    }
    @Test
    fun textEditOfMultiPartFormulaKeepsSeparateTotalsAndNames() {
        val original = listOf(
            RollSubgroup("attack", "Attack", "1d20"),
            RollSubgroup("damage", "Damage", "2d6"),
        )

        val imported = requireNotNull(guidedSubgroupsFromAdvancedExpression(
            expression = "(1d20+3)+(2d6)",
            current = original,
            level = 4,
            modifiers = emptyList(),
        ))

        assertEquals(2, imported.size)
        assertEquals("attack", imported[0].id)
        assertEquals("Attack", imported[0].name)
        assertEquals("1d20+3", imported[0].expression)
        assertEquals("damage", imported[1].id)
        assertEquals("Damage", imported[1].name)
        assertEquals("2d6", imported[1].expression)
    }

    @Test
    fun reorganizedCanonicalPartsRetainIdsByExpression() {
        val original = listOf(
            RollSubgroup("attack", "Attack", "1d20"),
            RollSubgroup("damage", "Damage", "2d6"),
        )
        val imported = requireNotNull(guidedSubgroupsFromAdvancedExpression(
            expression = "(2d6)-(1d20)",
            current = original,
            level = 1,
            modifiers = emptyList(),
        ))

        assertEquals(listOf("damage", "attack"), imported.map { it.id })
        assertEquals(RollSubgroupOperator.SUBTRACT, imported[1].operator)
    }

}
