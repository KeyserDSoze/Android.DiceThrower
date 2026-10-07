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
            assertTrue(state.x in -DiceTableViewport.HALF_WIDTH..DiceTableViewport.HALF_WIDTH)
            assertTrue(state.y in -DiceTableViewport.HALF_HEIGHT..DiceTableViewport.HALF_HEIGHT)
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

    @Test
    fun cameraAlwaysFitsCompleteTableOnCommonPhoneViewports() {
        listOf(0.42f, 0.50f, 0.60f, 1f, 1.8f).forEach { aspect ->
            val distance = DiceTableViewport.cameraDistanceFor(aspect)
            val halfVertical = distance * kotlin.math.tan(Math.toRadians(18.0)).toFloat()
            val halfHorizontal = halfVertical * aspect

            assertTrue(halfVertical > DiceTableViewport.HALF_HEIGHT)
            assertTrue(halfHorizontal > DiceTableViewport.HALF_WIDTH)
        }
    }

    @Test
    fun visualBackgroundAlwaysCoversViewportWithoutChangingPhysicalBounds() {
        listOf(0.42f, 0.50f, 0.5625f, 0.60f, 1f, 1.8f).forEach { aspect ->
            val distance = DiceTableViewport.cameraDistanceFor(aspect)
            val bounds = DiceTableViewport.visualBoundsFor(aspect, distance)
            val planeDistance = distance - DiceTableViewport.TABLE_Z
            val projectedHalfHeight = planeDistance * kotlin.math.tan(Math.toRadians(18.0)).toFloat()
            val projectedHalfWidth = projectedHalfHeight * aspect

            assertTrue(bounds.halfHeight > projectedHalfHeight)
            assertTrue(bounds.halfWidth > projectedHalfWidth)
            assertTrue(DiceTableViewport.HALF_WIDTH <= bounds.halfWidth)
            assertTrue(DiceTableViewport.HALF_HEIGHT <= bounds.halfHeight)
        }
    }

    @Test
    fun matchingPortraitPresetUsesWholeTextureForPhoneViewport() {
        val coordinates = tableTextureCoordinatesFor(
            imageAspect = 9f / 16f,
            targetAspect = 9f / 16f,
        )

        assertEquals(0f, coordinates[0], 0.0001f)
        assertEquals(1f, coordinates[1], 0.0001f)
        assertEquals(1f, coordinates[2], 0.0001f)
        assertEquals(0f, coordinates[5], 0.0001f)
    }

    @Test
    fun tablePhotoCoordinatesCenterCropWithoutStretching() {
        val targetAspect = 9f / 16f
        val portrait = tableTextureCoordinatesFor(0.35f, targetAspect)
        val landscape = tableTextureCoordinatesFor(1.5f, targetAspect)

        // A tall portrait keeps the full width and trims equally from top/bottom.
        assertEquals(0f, portrait[0], 0.0001f)
        assertEquals(1f, portrait[2], 0.0001f)
        assertTrue(portrait[1] < 1f)
        assertTrue(portrait[5] > 0f)

        // A wide image keeps the full height and trims equally from left/right.
        assertTrue(landscape[0] > 0f)
        assertTrue(landscape[2] < 1f)
        assertEquals(1f, landscape[1], 0.0001f)
        assertEquals(0f, landscape[5], 0.0001f)
    }
}
