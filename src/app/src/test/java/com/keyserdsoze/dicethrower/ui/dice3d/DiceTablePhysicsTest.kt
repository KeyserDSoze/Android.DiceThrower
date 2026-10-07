package com.keyserdsoze.dicethrower.ui.dice3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
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
    fun settleProgressIsSmoothMonotonicAndCompleteBeforeSettled() {
        val physics = DiceTablePhysics(count = 4, seed = 19L)
        var previous = 0f

        repeat(220) {
            physics.step(1f / 60f)
            assertTrue(physics.settleProgress + 0.0001f >= previous)
            previous = physics.settleProgress
        }

        assertEquals(1f, physics.settleProgress, 0.0001f)
        assertTrue(physics.isSettled)
    }

    @Test
    fun fullFaceCorrectionPointsResolvedFaceTowardCamera() {
        listOf(6, 8, 12, 20).forEach { sides ->
            val mesh = DiceMeshFactory.create(sides)
            val normal = Vec3(
                mesh.valueFaceNormals[0],
                mesh.valueFaceNormals[1],
                mesh.valueFaceNormals[2],
            )
            val moving = rotateFaceNormal(normal, 47f, -113f, 72f)
            val correction = faceAlignmentCorrection(moving, 1f)
            val corrected = rotateAroundAxis(moving, correction)

            assertEquals(0f, corrected.x, 0.0015f)
            assertEquals(0f, corrected.y, 0.0015f)
            assertTrue(corrected.z > 0.999f)
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

    private fun rotateAroundAxis(vector: Vec3, rotation: DiceAxisAngle): Vec3 {
        val axis = rotation.axis.normalized()
        val radians = Math.toRadians(rotation.angleDegrees.toDouble())
        val c = cos(radians).toFloat()
        val si = sin(radians).toFloat()
        return vector * c +
            axis.cross(vector) * si +
            axis * (axis.dot(vector) * (1f - c))
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
