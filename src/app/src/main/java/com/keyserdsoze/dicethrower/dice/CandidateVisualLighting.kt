package com.keyserdsoze.dicethrower.dice

/** Pure scene brightness policy, derived only from the logical winner metadata. */
object CandidateVisualLighting {
    fun brightness(revealed: Boolean, enabled: Boolean, discarded: Boolean): Float =
        if (revealed && enabled && discarded) 0.42f else 1f

    fun chosenPulse(
        revealed: Boolean, enabled: Boolean, chosen: Boolean, elapsedSeconds: Float,
    ): Float = if (revealed && enabled && chosen) {
        0.34f * (1f - elapsedSeconds.coerceAtLeast(0f)).coerceIn(0f, 1f)
    } else 0f
}
