package com.keyserdsoze.dicethrower.ui.dice3d

import com.keyserdsoze.dicethrower.model.DiceTableTheme
import org.junit.Assert.assertEquals
import org.junit.Test

class DiceSceneUpdateTest {
    private val current = DiceSceneKey(123, DiceTableTheme.ARCANE, "arcane", true)

    @Test
    fun firstSceneOrNewThrowUpdatesTheRoll() {
        assertEquals(DiceSceneUpdate.ROLL, diceSceneUpdate(null, current, false))
        assertEquals(DiceSceneUpdate.ROLL, diceSceneUpdate(current, current.copy(eventId = 124), false))
    }

    @Test
    fun identicalUpdatesDoNothing() {
        assertEquals(DiceSceneUpdate.NONE, diceSceneUpdate(current, current.copy(), false))
    }

    @Test
    fun aLateLoadedBitmapUpdatesOnlyTheTable() {
        assertEquals(DiceSceneUpdate.TABLE, diceSceneUpdate(current, current, true))
    }

    @Test
    fun changingArtworkOrThemeDoesNotReplayPhysics() {
        assertEquals(DiceSceneUpdate.TABLE, diceSceneUpdate(current, current.copy(tableImageKey = "custom"), false))
        assertEquals(DiceSceneUpdate.TABLE, diceSceneUpdate(current, current.copy(tableTheme = DiceTableTheme.OAK), false))
    }

    @Test
    fun disablingMotionUpdatesTheRollEvenIfArtworkAlsoChanges() {
        assertEquals(DiceSceneUpdate.ROLL, diceSceneUpdate(current,
            current.copy(animateRoll = false, tableTheme = DiceTableTheme.OAK), true))
    }
}
