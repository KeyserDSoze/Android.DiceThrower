package com.keyserdsoze.dicethrower.ui.v2

import com.keyserdsoze.dicethrower.dice.DiceComponent
import com.keyserdsoze.dicethrower.dice.DiceRollResult
import com.keyserdsoze.dicethrower.dice.DiceRollVisualEvent
import com.keyserdsoze.dicethrower.dice.DoubleRollEvaluation
import com.keyserdsoze.dicethrower.dice.EffectsVisualStage
import com.keyserdsoze.dicethrower.model.CharacterModifier
import com.keyserdsoze.dicethrower.model.DoubleRollMode
import com.keyserdsoze.dicethrower.model.RollDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RollSessionTest {
    private val roll = RollDefinition("roll", "hero", "Roll", "1d6+3")
    private val result = DiceRollResult(7, listOf(DiceComponent(1, 6, 1, listOf(4))), 3)
    private val preview = DiceRollVisualEvent(-1, result, emptyList())
    private fun session() = RollSession(RollSessionKey(roll, 2, emptyList(), "1d6+3"), preview)

    @Test
    fun detachOfAPreviewNeverInventsAResult() {
        val session = session()
        session.detach()
        assertFalse(session.hasRolled.value)
        assertFalse(session.resultRevealed.value)
        assertFalse(session.restored.value)
        assertSame(preview, session.visualEvent.value)
    }

    @Test
    fun interruptedAnimationRestoresTheAlreadySampledFinalDiceAndComparison() {
        val session = session()
        val extra = DiceComponent(1, 8, 1, listOf(6))
        val finalEvent = DiceRollVisualEvent(20, DiceRollResult(10, result.components + extra, 0), emptyList())
        val comparison = DoubleRollEvaluation(DoubleRollMode.BEST, result, result, emptyList(), 7, 4)
        session.hasRolled.value = true
        session.outcome.value = result
        session.doubleEvaluation.value = comparison
        session.finalRollTotal.value = 13
        session.visualEvent.value = preview.copy(id = 10)
        session.finalVisualEvent.value = finalEvent
        session.visualStages.value = listOf(EffectsVisualStage(result, emptyMap()), EffectsVisualStage(finalEvent.result, emptyMap()))
        session.detach()
        assertSame(result, session.outcome.value)
        assertSame(comparison, session.doubleEvaluation.value)
        assertSame(finalEvent, session.visualEvent.value)
        assertEquals(13, session.finalRollTotal.value)
        assertEquals(1, session.visualStageIndex.value)
        assertTrue(session.resultRevealed.value)
        assertTrue(session.restored.value)
    }

    @Test
    fun oldOrRepeatedSettleCallbacksCannotAdvanceARevealedSession() {
        val session = session()
        session.hasRolled.value = true
        session.visualEvent.value = preview.copy(id = 20)
        assertFalse(session.acceptsSettledEvent(10))
        assertTrue(session.acceptsSettledEvent(20))
        session.resultRevealed.value = true
        assertFalse(session.acceptsSettledEvent(20))
        session.resultRevealed.value = false
        session.detach()
        assertFalse(session.acceptsSettledEvent(20))
    }

    @Test
    fun cooldownAndAnimationBothGateTheRealClickHandler() {
        val session = session()
        assertTrue(session.acceptRequest(0)) // Zero is a valid first timestamp, not a sentinel.
        assertFalse(session.controlsReady)
        assertFalse(session.acceptRequest(1))
        session.hasRolled.value = true
        session.completeCooldown(0)
        assertFalse(session.controlsReady) // Still physically rolling.
        assertFalse(session.acceptRequest(2))
        session.resultRevealed.value = true
        assertTrue(session.controlsReady)
        assertTrue(session.acceptRequest(3))
    }

    @Test
    fun aCancelledOlderCooldownCannotEnableTheNextThrow() {
        val session = session()
        session.acceptRequest(1)
        session.completeCooldown(1)
        session.acceptRequest(2)
        session.completeCooldown(1)
        assertFalse(session.controlsReady)
        session.completeCooldown(2)
        assertTrue(session.controlsReady)
    }

    @Test
    fun definitionsLevelsAndEffectVariablesInvalidateTheSessionKey() {
        val modifier = CharacterModifier("mod", "hero", "Strength", 3)
        val key = RollSessionKey(roll, 2, listOf(modifier), "1d6+3")
        assertEquals(key, key.copy())
        assertFalse(key == key.copy(characterLevel = 3))
        assertFalse(key == key.copy(roll = roll.copy(expression = "1d8+3")))
        // An unused roll variable can still affect an Effect: include the modifier values too.
        assertFalse(key == key.copy(modifiers = listOf(modifier.copy(value = 4))))
    }
}
