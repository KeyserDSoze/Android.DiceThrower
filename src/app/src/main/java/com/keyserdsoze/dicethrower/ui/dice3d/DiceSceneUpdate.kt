package com.keyserdsoze.dicethrower.ui.dice3d

import com.keyserdsoze.dicethrower.model.DiceTableTheme

internal data class DiceSceneKey(
    val eventId: Long,
    val tableTheme: DiceTableTheme,
    val tableImageKey: String?,
    val animateRoll: Boolean,
)

internal enum class DiceSceneUpdate { NONE, TABLE, ROLL }

/** Loading/changing artwork must not replay physics or emit another settle callback. */
internal fun diceSceneUpdate(previous: DiceSceneKey?, next: DiceSceneKey, imageChanged: Boolean): DiceSceneUpdate = when {
    previous == null || previous.eventId != next.eventId || previous.animateRoll != next.animateRoll -> DiceSceneUpdate.ROLL
    previous != next || imageChanged -> DiceSceneUpdate.TABLE
    else -> DiceSceneUpdate.NONE
}
