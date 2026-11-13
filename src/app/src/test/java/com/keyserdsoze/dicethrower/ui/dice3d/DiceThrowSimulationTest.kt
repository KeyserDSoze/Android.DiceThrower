package com.keyserdsoze.dicethrower.ui.dice3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class DiceThrowSimulationTest {
    private val tolerance = 0.03f

    private fun vertices(sides: Int, pose: DiePose, scale: Float): List<Vec3> =
        DieGeometries.forSides(sides).vertices.map { pose.position + pose.rotation.rotate(it) * scale }

    private fun bake(specs: List<DieThrowSpec>, seed: Long, scale: Float = DiceTableViewport.dieScaleFor(specs.size), edge: Boolean = false) =
        DiceThrowSimulator.bake(specs, seed, scale, spawnFromEdge = edge)

    @Test
    fun everyDieLandsShowingTheValueChosenByTheEngine() {
        listOf(2, 3, 4, 6, 8, 10, 12, 20, 100).forEach { sides ->
            val geometry = DieGeometries.forSides(sides)
            val values = if (sides == 100) listOf(1, 10, 37, 64, 99, 100) else (1..sides).toList()
            values.forEach { value ->
                listOf(11L, 977L).forEach { seed ->
                    val baked = bake(listOf(DieThrowSpec(sides, value)), seed * 31 + value)
                    val pose = baked.finalPose(0)
                    val top = geometry.upAnchorIndex(pose.rotation)
                    assertEquals("d$sides value $value seed $seed", geometry.labelForValue(value), geometry.anchorLabels[top])
                    val down = geometry.faceNormals.minOf { pose.rotation.rotate(it).z }
                    assertTrue("d$sides must rest flat ($down)", down < -0.9995f)
                }
            }
        }
    }

    @Test
    fun throwsAreDeterministicPerSeed() {
        val specs = List(6) { DieThrowSpec(listOf(4, 6, 8, 10, 12, 20)[it], 2) }
        val first = bake(specs, 42L)
        val second = bake(specs, 42L)
        assertEquals(first.sampleCount, second.sampleCount)
        (0 until first.sampleCount step 7).forEach { sample ->
            val time = sample / first.sampleRate
            specs.indices.forEach { die -> assertEquals(first.pose(die, time), second.pose(die, time)) }
        }
    }

    @Test
    fun crowdedThrowsStayOnTheTableSettleQuicklyAndNeverInterpenetrate() {
        listOf(1, 3, 7, 12).forEach { count ->
            repeat(6) { round ->
                val specs = List(count) { DieThrowSpec(listOf(20, 6, 8, 12, 10, 4)[(it + round) % 6], 1) }
                val scale = DiceTableViewport.dieScaleFor(count)
                val baked = bake(specs, 1000L * count + round, scale)
                assertTrue("throw must be short (${baked.durationSeconds}s)", baked.durationSeconds in 0.8f..3.9f)
                var time = 0f
                while (time <= baked.durationSeconds) {
                    specs.forEachIndexed { index, spec ->
                        vertices(spec.sides, baked.pose(index, time), scale).forEach { v ->
                            assertTrue("x out of table: ${v.x}", v.x in -DiceTableViewport.HALF_WIDTH - tolerance..DiceTableViewport.HALF_WIDTH + tolerance)
                            assertTrue("y out of table: ${v.y}", v.y in -DiceTableViewport.HALF_HEIGHT - tolerance..DiceTableViewport.HALF_HEIGHT + tolerance)
                            assertTrue("below felt: ${v.z}", v.z > -tolerance)
                        }
                    }
                    time += 1f / 30f
                }
                val finals = specs.indices.map { baked.finalPose(it) }
                finals.forEachIndexed { i, a ->
                    finals.drop(i + 1).forEachIndexed { offset, b ->
                        val j = i + 1 + offset
                        val minimum = (DieGeometries.forSides(specs[i].sides).inradius + DieGeometries.forSides(specs[j].sides).inradius) * scale * 0.92f
                        assertTrue("dice $i/$j overlap at rest", (a.position - b.position).length() >= minimum)
                    }
                }
            }
        }
    }

    @Test
    fun candidateLanesNeverMixDuringOrAfterTheThrow() {
        listOf(1, 6, 11).forEach { groupA ->
            val specs = List(12) { DieThrowSpec(20, 7, lane = if (it < groupA) 0 else 1) }
            val scale = DiceTableViewport.dieScaleFor(specs.size, largestLane = maxOf(groupA, 12 - groupA))
            val baked = bake(specs, 1200L + groupA, scale)
            var time = 0f
            while (time <= baked.durationSeconds) {
                specs.forEachIndexed { index, spec ->
                    val (minY, maxY) = CandidateLaneLayout.band(spec.lane, DiceTableViewport.HALF_HEIGHT)
                    vertices(spec.sides, baked.pose(index, time), scale).forEach { v ->
                        assertTrue("die $index left lane ${spec.lane} at $time: ${v.y}", v.y >= minY - tolerance && v.y <= maxY + tolerance)
                    }
                }
                time += 1f / 30f
            }
        }
        assertTrue(shouldResolveDiceCollision(null, 0))
        assertTrue(shouldResolveDiceCollision(1, 1))
        assertTrue(!shouldResolveDiceCollision(0, 1))
    }

    @Test
    fun alreadySettledEffectDiceStayStillWhileNewDiceFlyInFromTheRails() {
        val scale = DiceTableViewport.dieScaleFor(3)
        val resting = DiceThrowSimulator.restingPose(20, 20, 0f, 0.5f, 0.2f, scale)
        val specs = listOf(DieThrowSpec(20, 20, fixedPose = resting), DieThrowSpec(6, 3), DieThrowSpec(6, 5))
        val baked = bake(specs, 813L, scale, edge = true)
        assertTrue(baked.pose(1, 0f).position.x < -1.5f)
        assertTrue(baked.pose(2, 0f).position.x > 1.5f)
        var time = 0f
        while (time <= baked.durationSeconds) {
            assertEquals(resting, baked.pose(0, time))
            time += 0.1f
        }
        listOf(1 to 3, 2 to 5).forEach { (index, value) ->
            val geometry = DieGeometries.forSides(6)
            assertEquals(value.toString(), geometry.anchorLabels[geometry.upAnchorIndex(baked.finalPose(index).rotation)])
        }
    }

    @Test
    fun diceReallyFlyBounceAndRollInsteadOfSlidingFlat() {
        val scale = DiceTableViewport.dieScaleFor(1)
        val baked = bake(listOf(DieThrowSpec(6, 4)), 5L, scale)
        val rest = DieGeometries.forSides(6).inradius * scale
        var maxHeight = 0f
        var rising = 0
        var previousZ = baked.pose(0, 0f).position.z
        var falling = false
        var time = 0f
        while (time <= baked.durationSeconds) {
            val z = baked.pose(0, time).position.z
            maxHeight = maxOf(maxHeight, z)
            if (z > previousZ + 1e-4f && falling) rising++
            falling = z < previousZ - 1e-4f
            previousZ = z
            time += 1f / 120f
        }
        assertTrue("thrown from above the felt", maxHeight > rest + 1f)
        assertTrue("bounces at least once", rising >= 1)
        val start = baked.pose(0, 0f)
        val end = baked.finalPose(0)
        assertTrue("travels across the table", (end.position - start.position).let { sqrt(it.x * it.x + it.y * it.y) } > 1.5f)
    }

    @Test
    fun bakingTwelveDiceIsCheapEnoughForTheRenderThread() {
        val specs = List(12) { DieThrowSpec(listOf(20, 12, 10, 8, 6, 4)[it % 6], 1) }
        bake(specs, 1L)
        val started = System.nanoTime()
        repeat(5) { bake(specs, it + 2L) }
        val averageMillis = (System.nanoTime() - started) / 5 / 1_000_000.0
        println("Average 12-dice bake: ${"%.1f".format(averageMillis)} ms")
        assertTrue("bake too slow: $averageMillis ms", averageMillis < 400.0)
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
            assertTrue(bounds.halfHeight > projectedHalfHeight)
            assertTrue(bounds.halfWidth > projectedHalfHeight * aspect)
            assertTrue(DiceTableViewport.HALF_WIDTH <= bounds.halfWidth)
            assertTrue(DiceTableViewport.HALF_HEIGHT <= bounds.halfHeight)
        }
    }

    @Test
    fun tablePhotoCoordinatesCenterCropWithoutStretching() {
        val same = tableTextureCoordinatesFor(imageAspect = 9f / 16f, targetAspect = 9f / 16f)
        assertEquals(0f, same[0], 0.0001f)
        assertEquals(1f, same[1], 0.0001f)
        assertEquals(1f, same[2], 0.0001f)
        assertEquals(0f, same[5], 0.0001f)
        val portrait = tableTextureCoordinatesFor(0.35f, 9f / 16f)
        val landscape = tableTextureCoordinatesFor(1.5f, 9f / 16f)
        assertEquals(0f, portrait[0], 0.0001f)
        assertEquals(1f, portrait[2], 0.0001f)
        assertTrue(portrait[1] < 1f)
        assertTrue(portrait[5] > 0f)
        assertTrue(landscape[0] > 0f)
        assertTrue(landscape[2] < 1f)
        assertEquals(1f, landscape[1], 0.0001f)
        assertEquals(0f, landscape[5], 0.0001f)
    }
}
