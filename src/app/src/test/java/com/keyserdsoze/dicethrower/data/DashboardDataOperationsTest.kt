package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.RollDefinition
import com.keyserdsoze.dicethrower.model.RollGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardDataOperationsTest {
    @Test
    fun removingRollFromGroupAppendsItToDashboard() {
        val result = DashboardDataOperations.changeRollGroup(
            data = fixture(),
            characterId = "character",
            rollId = "group-roll-a",
            newGroupId = null,
        )

        val roll = result.rolls.single { it.id == "group-roll-a" }
        assertEquals(null, roll.groupId)
        assertEquals(2, roll.order)
    }

    @Test
    fun movingRollToAnotherGroupAppendsItInsideDestination() {
        val data = fixture().copy(
            groups = fixture().groups + RollGroup("group-b", "character", "Defense", 2),
            rolls = fixture().rolls + RollDefinition("group-roll-b", "character", "Guard", "1d20", "group-b", order = 0),
        )

        val result = DashboardDataOperations.changeRollGroup(
            data = data,
            characterId = "character",
            rollId = "group-roll-a",
            newGroupId = "group-b",
        )

        val roll = result.rolls.single { it.id == "group-roll-a" }
        assertEquals("group-b", roll.groupId)
        assertEquals(1, roll.order)
    }

    @Test
    fun deletingGroupKeepsItsRollsAndAppendsThemToDashboard() {
        val data = fixture().copy(
            rolls = fixture().rolls + RollDefinition("group-roll-b", "character", "Ice", "1d6", "group-a", order = 1),
        )

        val result = DashboardDataOperations.deleteGroup(data, "character", "group-a")

        assertEquals(emptyList<RollGroup>(), result.groups)
        assertEquals(
            listOf("loose-roll" to 1, "group-roll-a" to 2, "group-roll-b" to 3),
            result.rolls.sortedBy { it.order }.map { it.id to it.order },
        )
        assertEquals(setOf<String?>(null), result.rolls.map { it.groupId }.toSet())
    }

    @Test
    fun topLevelReorderKeepsGroupAndLooseRollInOneOrder() {
        val result = DashboardDataOperations.moveTopLevel(
            data = fixture(),
            characterId = "character",
            key = "roll-loose-roll",
            direction = -1,
        )

        assertEquals(1, result.groups.single().order)
        assertEquals(0, result.rolls.single { it.id == "loose-roll" }.order)
    }

    private fun fixture() = AppData(
        groups = listOf(RollGroup("group-a", "character", "Spells", 0)),
        rolls = listOf(
            RollDefinition("loose-roll", "character", "Initiative", "1d20", order = 1),
            RollDefinition("group-roll-a", "character", "Fire", "1d6", "group-a", order = 0),
        ),
    )
}
