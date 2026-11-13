package com.keyserdsoze.dicethrower.ui.dice3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DieGeometryTest {
    private val allSides = listOf(2, 3, 4, 6, 8, 10, 12, 20, 100)

    private fun values(sides: Int): IntRange = 1..sides

    @Test
    fun rotationGroupsHaveTheirMathematicalOrder() {
        val expected = mapOf(2 to 64, 3 to 24, 4 to 12, 6 to 24, 8 to 24, 10 to 10, 12 to 60, 20 to 60, 100 to 10)
        expected.forEach { (sides, order) ->
            assertEquals("d$sides symmetry group", order, DieGeometries.forSides(sides).symmetries.size)
        }
    }

    @Test
    fun everyLogicalValueHasAVisibleAnchor() {
        allSides.forEach { sides ->
            val geometry = DieGeometries.forSides(sides)
            values(sides).forEach { value ->
                val label = geometry.labelForValue(value)
                assertTrue("d$sides value $value -> '$label'", label in geometry.anchorLabels)
            }
        }
        val percentile = DieGeometries.forSides(100)
        assertEquals("00", percentile.labelForValue(100))
        assertEquals("00", percentile.labelForValue(7))
        assertEquals("50", percentile.labelForValue(57))
        assertEquals("90", percentile.labelForValue(99))
    }

    @Test
    fun oppositeFacesAddUpLikeRealDice() {
        mapOf(6 to 7, 8 to 9, 10 to 11, 12 to 13, 20 to 21, 100 to 90).forEach { (sides, total) ->
            val geometry = DieGeometries.forSides(sides)
            geometry.anchors.forEachIndexed { index, anchor ->
                val opposite = geometry.anchors.indices.minBy { geometry.anchors[it].dot(anchor) }
                assertEquals("d$sides opposite of ${geometry.anchorLabels[index]}",
                    total, geometry.anchorLabels[index].toInt() + geometry.anchorLabels[opposite].toInt())
            }
        }
    }

    @Test
    fun remapIsASymmetryThatBringsTheRequestedValueOnTop() {
        allSides.forEach { sides ->
            val geometry = DieGeometries.forSides(sides)
            val sampled = if (sides == 100) listOf(1, 9, 10, 55, 99, 100) else values(sides).toList()
            geometry.anchors.indices.forEach { landed ->
                val finalRotation = (Quat.fromAxisAngle(Vec3.UNIT_Z, landed * 0.7f) *
                    Quat.between(geometry.anchors[landed], Vec3.UNIT_Z)).normalized()
                sampled.forEach { value ->
                    val remap = geometry.remapFor(value, finalRotation)
                    val shown = geometry.anchorIndicesForValue(value).maxOf { (finalRotation * remap).rotate(geometry.anchors[it]).z }
                    assertTrue("d$sides value $value onto anchor $landed", shown > 0.9995f)
                    geometry.vertices.forEach { vertex ->
                        val mapped = remap.rotate(vertex)
                        assertTrue("d$sides remap must preserve the hull", geometry.vertices.any { (it - mapped).length() < 2e-3f })
                    }
                }
            }
        }
    }

    @Test
    fun topNumeralIsTurnedAsUprightAsTheFaceSymmetryAllows() {
        // A triangle can be read within ±60°, a square ±45°, a pentagon ±36° of upright.
        mapOf(6 to 0.70f, 8 to 0.49f, 12 to 0.80f, 20 to 0.49f).forEach { (sides, minimumUp) ->
            val geometry = DieGeometries.forSides(sides)
            repeat(12) { turn ->
                val finalRotation = (Quat.fromAxisAngle(Vec3.UNIT_Z, turn * 0.53f) *
                    Quat.between(geometry.anchors[turn % geometry.anchors.size], Vec3.UNIT_Z)).normalized()
                val value = (turn * 7) % sides + 1
                val remap = geometry.remapFor(value, finalRotation)
                val glyph = geometry.glyphs.first { it.label == value.toString() }
                val up = (finalRotation * remap).rotate(glyph.up)
                assertTrue("d$sides value $value tilted: ${up.y}", up.y >= minimumUp)
            }
        }
    }

    @Test
    fun d4IsReadAtTheTopVertexAndRestsOnAFace() {
        val geometry = DieGeometries.forSides(4)
        (1..4).forEach { value ->
            val pose = DiceThrowSimulator.restingPose(4, value, 0f, 0f, 0.3f, 1f)
            val top = geometry.anchors.indices.maxBy { pose.rotation.rotate(geometry.anchors[it]).z }
            assertEquals(value.toString(), geometry.anchorLabels[top])
            val down = geometry.faceNormals.minOf { pose.rotation.rotate(it).z }
            assertTrue("a d4 must lie flat on a face", down < -0.9995f)
            // Every face shows the value of each of its three corners.
            assertEquals(12, geometry.glyphs.size)
        }
    }

    @Test
    fun restingPoseLiesFlatOnTheFeltWithTheValueUp() {
        allSides.forEach { sides ->
            val geometry = DieGeometries.forSides(sides)
            val sampled = if (sides == 100) listOf(5, 42, 100) else values(sides).toList()
            sampled.forEach { value ->
                val pose = DiceThrowSimulator.restingPose(sides, value, 0.4f, -1f, 1.1f, 0.7f)
                val top = geometry.upAnchorIndex(pose.rotation)
                assertEquals("d$sides value $value", geometry.labelForValue(value), geometry.anchorLabels[top])
                val lowest = geometry.vertices.minOf { pose.rotation.rotate(it).z * 0.7f + pose.position.z }
                assertEquals(0f, lowest, 1e-4f)
            }
        }
    }

    @Test
    fun inertiaIsPositiveAndIsotropicForPlatonicSolids() {
        allSides.forEach { sides ->
            val inertia = DieGeometries.forSides(sides).inertiaDiagonal
            assertTrue(inertia.x > 0f && inertia.y > 0f && inertia.z > 0f)
        }
        listOf(4, 6, 8, 12, 20).forEach { sides ->
            val inertia = DieGeometries.forSides(sides).inertiaDiagonal
            assertTrue("d$sides", abs(inertia.x - inertia.y) < 1e-3f && abs(inertia.y - inertia.z) < 1e-3f)
        }
    }

    @Test
    fun numeralsSitInsideTheirFace() {
        allSides.forEach { sides ->
            val geometry = DieGeometries.forSides(sides)
            geometry.glyphs.forEach { glyph ->
                val offset = glyph.normal.dot(glyph.center)
                val face = geometry.faceNormals.indices.maxBy { geometry.faceNormals[it].dot(glyph.normal) }
                assertEquals("d$sides glyph on its face plane", geometry.faceOffsets[face], offset, 1e-3f)
                assertEquals(1f, glyph.right.cross(glyph.up).dot(glyph.normal), 1e-3f)
                assertTrue(glyph.size > 0.1f)
            }
        }
    }
}
