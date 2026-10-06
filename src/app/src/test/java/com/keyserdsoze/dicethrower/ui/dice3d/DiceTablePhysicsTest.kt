package com.keyserdsoze.dicethrower.ui.dice3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class DiceTablePhysicsTest {
    @Test
    fun simulationIsDeterministicAndKeepsDiceInsideTable() {
        val first = DiceTablePhysics(count = 8, seed = 42L)
        val second = DiceTablePhysics(count = 8, seed = 42L)

        repeat(180) {
            first.step(1f / 60f)
            second.step(1f / 60f)
        }

        assertEquals(first.states(), second.states())
        first.states().forEach { state ->
            assertTrue(state.x in -2.55f..2.55f)
            assertTrue(state.y in -3.25f..3.25f)
        }
    }

    @Test
    fun simulationAlwaysSettles() {
        val physics = DiceTablePhysics(count = 12, seed = 91L)

        repeat(220) { physics.step(1f / 60f) }

        assertTrue(physics.isSettled)
        val states = physics.states()
        states.forEachIndexed { index, first ->
            states.drop(index + 1).forEach { second ->
                val dx = second.x - first.x
                val dy = second.y - first.y
                assertTrue(sqrt(dx * dx + dy * dy) >= physics.radius * 1.95f)
            }
        }
    }
}
