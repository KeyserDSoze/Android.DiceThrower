package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.RollDefinition

object DashboardDataOperations {
    fun moveTopLevel(
        data: AppData,
        characterId: String,
        key: String,
        direction: Int,
    ): AppData {
        val entries = topLevelKeys(data, characterId).toMutableList()
        val index = entries.indexOf(key)
        val target = index + direction
        if (index < 0 || target !in entries.indices) return data

        val tmp = entries[index]
        entries[index] = entries[target]
        entries[target] = tmp
        val orderByKey = entries.mapIndexed { order, entryKey -> entryKey to order }.toMap()

        return data.copy(
            groups = data.groups.map { group ->
                orderByKey[groupKey(group.id)]?.let { group.copy(order = it) } ?: group
            },
            rolls = data.rolls.map { roll ->
                if (roll.groupId == null) {
                    orderByKey[rollKey(roll.id)]?.let { roll.copy(order = it) } ?: roll
                } else {
                    roll
                }
            },
        )
    }

    fun moveRollInsideGroup(
        all: List<RollDefinition>,
        characterId: String,
        groupId: String,
        rollId: String,
        direction: Int,
    ): List<RollDefinition> {
        val local = all
            .filter { it.characterId == characterId && it.groupId == groupId }
            .sortedBy { it.order }
            .toMutableList()
        val index = local.indexOfFirst { it.id == rollId }
        val target = index + direction
        if (index < 0 || target !in local.indices) return all

        val tmp = local[index]
        local[index] = local[target]
        local[target] = tmp
        val order = local.mapIndexed { i, roll -> roll.id to i }.toMap()
        return all.map { roll -> order[roll.id]?.let { roll.copy(order = it) } ?: roll }
    }

    fun changeRollGroup(
        data: AppData,
        characterId: String,
        rollId: String,
        newGroupId: String?,
    ): AppData {
        val roll = data.rolls.firstOrNull { it.id == rollId && it.characterId == characterId } ?: return data
        if (roll.groupId == newGroupId) return data

        val others = data.rolls.filterNot { it.id == rollId }
        val order = if (newGroupId == null) {
            nextTopLevelOrder(data.copy(rolls = others), characterId)
        } else {
            nextGroupRollOrder(data.copy(rolls = others), characterId, newGroupId)
        }
        return data.copy(rolls = others + roll.copy(groupId = newGroupId, order = order))
    }

    fun deleteGroup(
        data: AppData,
        characterId: String,
        groupId: String,
    ): AppData {
        val remainingGroups = data.groups.filterNot { it.id == groupId }
        val dataWithoutGroup = data.copy(groups = remainingGroups)
        var nextOrder = nextTopLevelOrder(dataWithoutGroup, characterId)
        val rolls = data.rolls.map { roll ->
            if (roll.characterId == characterId && roll.groupId == groupId) {
                roll.copy(groupId = null, order = nextOrder++)
            } else {
                roll
            }
        }
        return data.copy(groups = remainingGroups, rolls = rolls)
    }

    private fun topLevelKeys(data: AppData, characterId: String): List<String> =
        (
            data.groups
                .filter { it.characterId == characterId }
                .map { groupKey(it.id) to it.order } +
                data.rolls
                    .filter { it.characterId == characterId && it.groupId == null }
                    .map { rollKey(it.id) to it.order }
            )
            .sortedWith(compareBy<Pair<String, Int>> { it.second }.thenBy { it.first })
            .map { it.first }

    fun nextTopLevelOrder(data: AppData, characterId: String): Int =
        (
            data.groups.filter { it.characterId == characterId }.map { it.order } +
                data.rolls.filter { it.characterId == characterId && it.groupId == null }.map { it.order }
            )
            .maxOrNull()
            ?.plus(1)
            ?: 0

    fun nextGroupRollOrder(data: AppData, characterId: String, groupId: String): Int =
        data.rolls
            .filter { it.characterId == characterId && it.groupId == groupId }
            .maxOfOrNull { it.order }
            ?.plus(1)
            ?: 0

    private fun groupKey(id: String) = "group-$id"

    private fun rollKey(id: String) = "roll-$id"
}
