package com.keyserdsoze.dicethrower.model

/**
 * A prepared Roll never disappears from storage: availability is derived from
 * the current character level and the independent manual enabled switch.
 * The Edit dashboard deliberately ignores this predicate.
 */
object RollLevelAvailability {
    fun isAvailable(roll: RollDefinition, characterLevel: Int): Boolean =
        roll.enabled && roll.minimumLevel in 1..9999 &&
            characterLevel in 1..9999 && characterLevel >= roll.minimumLevel

    fun usable(rolls: List<RollDefinition>, characterId: String, characterLevel: Int): List<RollDefinition> =
        rolls.filter { it.characterId == characterId && isAvailable(it, characterLevel) }
}
