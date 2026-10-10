package com.keyserdsoze.dicethrower.ui.dice3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class DiceTablePhysicsTest {
    @Test
    fun neutralPartDiceCollideWithEitherCandidateWithoutMixingTheTwoCandidates() {
        assertTrue(shouldResolveDiceCollision(null, 0))
        assertTrue(shouldResolveDiceCollision(0, null))
        assertTrue(shouldResolveDiceCollision(null, 1))
        assertTrue(shouldResolveDiceCollision(1, null))
        assertTrue(shouldResolveDiceCollision(0, 0))
        assertTrue(shouldResolveDiceCollision(1, 1))
        assertTrue(shouldResolveDiceCollision(null, null))
        assertTrue(!shouldResolveDiceCollision(0, 1))
        assertTrue(!shouldResolveDiceCollision(1, 0))

        val physics = DiceTablePhysics(
            count = 4, seed = 275L,
            laneByDieIndex = mapOf(0 to 0, 1 to 1),
        )
        repeat(220) { physics.step(1f / 60f) }
        assertTrue(physics.isSettled)
        assertTrue(physics.states()[0].y > 0f)
        assertTrue(physics.states()[1].y < 0f)
        physics.states().forEach {
            assertTrue(it.x in -DiceTableViewport.HALF_WIDTH..DiceTableViewport.HALF_WIDTH)
            assertTrue(it.y in -DiceTableViewport.HALF_HEIGHT..DiceTableViewport.HALF_HEIGHT)
        }
    }

    @Test
    fun generatedEffectDiceEnterFromEdgesWithoutChangingLogicalValues() {
        val physics = DiceTablePhysics(count = 2, seed = 813L, spawnFromEdge = true)
        val starting = physics.states()
        assertTrue(starting[0].x < -1.5f)
        assertTrue(starting[1].x > 1.5f)
        repeat(200) { physics.step(1f / 60f) }
        assertTrue(physics.isSettled)
        physics.states().forEach { state ->
            assertTrue(state.x in -DiceTableViewport.HALF_WIDTH..DiceTableViewport.HALF_WIDTH)
            assertTrue(state.y in -DiceTableViewport.HALF_HEIGHT..DiceTableViewport.HALF_HEIGHT)
        }
    }


    @Test
    fun twoCandidatesStayInSeparateTableHalfLanesDuringAndAfterPhysics() {
        val physics = DiceTablePhysics(
            count = 6, seed = 993L,
            laneByDieIndex = (0..2).associateWith { 0 } + (3..5).associateWith { 1 },
        )
        repeat(230) {
            physics.step(1f / 60f)
            physics.states().forEachIndexed { index, state ->
                if (index < 3) assertTrue("A must remain above center", state.y > 0f)
                else assertTrue("B must remain below center", state.y < 0f)
            }
        }
        assertTrue(physics.isSettled)
    }


    @Test
    fun doubleRollDiceRemainInCompactLanesEvenWhenGroupsAreUneven() {
        // Eleven dice in one candidate is a stress case for the 12-die GPU cap.
        for (groupASize in listOf(1, 6, 11)) {
            val physics = DiceTablePhysics(
                count = 12, seed = 1200L + groupASize,
                laneByDieIndex = (0 until 12).associateWith { if (it < groupASize) 0 else 1 },
            )
            val top = CandidateLaneLayout.bounds(0, physics.radius, DiceTableViewport.HALF_HEIGHT)
            val bottom = CandidateLaneLayout.bounds(1, physics.radius, DiceTableViewport.HALF_HEIGHT)
            assertTrue(top.second <= 2.85f)
            assertTrue(bottom.first >= -2.85f)
            assertTrue(top.first > 0f && bottom.second < 0f)
            repeat(230) {
                physics.step(1f / 60f)
                physics.states().forEachIndexed { index, die ->
                    val limits = if (index < groupASize) top else bottom
                    assertTrue("Die $index escaped its compact lane at step $it: ${die.y} not in $limits",
                        die.y >= limits.first - 0.001f && die.y <= limits.second + 0.001f)
                }
            }
            assertTrue(physics.isSettled)
        }
    }

    @Test
    fun nonComparedDiceKeepFullTableBounds() {
        val radius = DiceTablePhysics(count = 2, seed = 9L).radius
        val bounds = CandidateLaneLayout.bounds(null, radius, DiceTableViewport.HALF_HEIGHT)
        assertEquals(-DiceTableViewport.HALF_HEIGHT + radius, bounds.first, 0.0001f)
        assertEquals(DiceTableViewport.HALF_HEIGHT - radius, bounds.second, 0.0001f)
    }

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
