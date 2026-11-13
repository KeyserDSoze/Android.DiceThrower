package com.keyserdsoze.dicethrower.data

import com.keyserdsoze.dicethrower.model.AppData
import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.RollLog
import org.junit.Assert.assertEquals
import org.junit.Test

class RollLogRetentionTest {
    private fun log(id: String, character: String, at: Long) =
        RollLog(id, character, "r", "Roll", "1d6", 3, "d", at)

    @Test
    fun rollingOneCharacterNeverTouchesAnotherCharactersLogs() {
        val old = listOf(log("a1", "a", 1), log("a2", "a", 2), log("b1", "b", 3))
        val updated = RollLogRetention.append(old, log("b2", "b", 10), retention = 2)
        assertEquals(listOf("a1", "a2", "b1", "b2"), updated.map { it.id })

        val trimmed = RollLogRetention.append(updated, log("b3", "b", 11), retention = 2)
        assertEquals(listOf("a1", "a2", "b2", "b3"), trimmed.map { it.id })
    }

    @Test
    fun otherCharacterRevisionIsUnchangedByTrimming() {
        val data = AppData(
            characters = listOf(CharacterProfile("a", "A"), CharacterProfile("b", "B")),
            logs = listOf(log("a1", "a", 1), log("a2", "a", 2), log("a3", "a", 3), log("b1", "b", 4)),
        )
        val before = CharacterRevision.revision(data, "a")
        val after = data.copy(logs = RollLogRetention.append(data.logs, log("b2", "b", 20), 1))
        assertEquals(before, CharacterRevision.revision(after, "a"))
        assertEquals(listOf("b2"), after.logs.filter { it.characterId == "b" }.map { it.id })
    }

    @Test
    fun zeroRetentionKeepsEverything() {
        val result = RollLogRetention.append(listOf(log("a1", "a", 1)), log("a2", "a", 2), 0)
        assertEquals(2, result.size)
    }
}
