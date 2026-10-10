package com.keyserdsoze.dicethrower.dice

import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateVisualLightingTest {
    @Test
    fun bothCandidatesStayIdenticallyBrightBeforeSettle() {
        for (discarded in listOf(true, false)) {
            assertEquals(1f, CandidateVisualLighting.brightness(
                revealed = false, enabled = true, discarded = discarded), 0.00001f)
            assertEquals(0f, CandidateVisualLighting.chosenPulse(
                revealed = false, enabled = true, chosen = !discarded, elapsedSeconds = 0f), 0f)
        }
    }

    @Test
    fun selectedWinnerGlowsAndDiscardedOnlyDimsAfterSettle() {
        assertEquals(0.42f, CandidateVisualLighting.brightness(true, true, true), 0f)
        assertEquals(1f, CandidateVisualLighting.brightness(true, true, false), 0f)
        assertEquals(0.34f, CandidateVisualLighting.chosenPulse(true, true, true, 0f), 0f)
        assertEquals(0f, CandidateVisualLighting.chosenPulse(true, true, true, 1.1f), 0f)
        assertEquals(0f, CandidateVisualLighting.chosenPulse(true, true, false, 0f), 0f)
    }

    @Test
    fun perRollSpotlightOffLeavesBothCandidatesFullyLit() {
        assertEquals(1f, CandidateVisualLighting.brightness(true, false, true), 0f)
        assertEquals(0f, CandidateVisualLighting.chosenPulse(true, false, true, 0f), 0f)
    }
}
