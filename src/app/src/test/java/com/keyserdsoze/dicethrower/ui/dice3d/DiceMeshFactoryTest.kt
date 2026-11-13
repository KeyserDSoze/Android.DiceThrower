package com.keyserdsoze.dicethrower.ui.dice3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class DiceMeshFactoryTest {
    private val allSides = listOf(2, 3, 4, 6, 8, 10, 12, 20, 100)

    @Test
    fun everyDieHasARoundedBodyAndOneQuadPerNumeral() {
        allSides.forEach { sides ->
            val mesh = DiceMeshFactory.create(sides)
            val geometry = DieGeometries.forSides(sides)
            assertTrue("d$sides body", mesh.bodyVertexCount > geometry.faces.size * 3)
            assertEquals(0, mesh.bodyVertexCount % 3)
            assertEquals("d$sides glyph quads", geometry.glyphs.size * 6, mesh.glyphVertexCount)
        }
    }

    @Test
    fun normalsAreUnitLengthAndTrianglesFaceOutwards() {
        allSides.forEach { sides ->
            val body = DiceMeshFactory.create(sides).body
            for (triangle in 0 until body.size / 18) {
                val o = triangle * 18
                val p0 = Vec3(body[o], body[o + 1], body[o + 2])
                val p1 = Vec3(body[o + 6], body[o + 7], body[o + 8])
                val p2 = Vec3(body[o + 12], body[o + 13], body[o + 14])
                val normalSum = (0..2).map { k -> Vec3(body[o + k * 6 + 3], body[o + k * 6 + 4], body[o + k * 6 + 5]) }
                normalSum.forEach { n -> assertEquals("d$sides normal", 1f, sqrt(n.dot(n)), 1e-3f) }
                val geometric = (p1 - p0).cross(p2 - p0)
                assertTrue("d$sides triangle $triangle winding", geometric.dot(normalSum.reduce(Vec3::plus)) > 0f)
            }
        }
    }

    @Test
    fun roundingNeverGrowsTheDieBeyondItsPhysicalHull() {
        allSides.forEach { sides ->
            val geometry = DieGeometries.forSides(sides)
            val body = DiceMeshFactory.create(sides).body
            for (vertex in 0 until body.size / 6) {
                val p = Vec3(body[vertex * 6], body[vertex * 6 + 1], body[vertex * 6 + 2])
                geometry.faceNormals.forEachIndexed { face, normal ->
                    assertTrue("d$sides vertex outside face $face", normal.dot(p) <= geometry.faceOffsets[face] + 1e-4f)
                }
            }
            // Faces keep their exact plane, so a resting rounded die still lies flat on the felt.
            val touchesEveryFace = geometry.faceNormals.indices.all { face ->
                (0 until body.size / 6).any { vertex ->
                    val p = Vec3(body[vertex * 6], body[vertex * 6 + 1], body[vertex * 6 + 2])
                    abs(geometry.faceNormals[face].dot(p) - geometry.faceOffsets[face]) < 1e-4f
                }
            }
            assertTrue("d$sides flat faces", touchesEveryFace)
        }
    }

    @Test
    fun glyphAtlasHasACellForEveryLabel() {
        allSides.forEach { sides ->
            DieGeometries.forSides(sides).anchorLabels.forEach { label ->
                assertTrue("missing atlas label $label", label in DiceGlyphAtlasLayout.labels)
            }
        }
        val uv = DiceGlyphAtlasLayout.uvRect("20")
        assertTrue(uv[0] < uv[2] && uv[1] < uv[3])
        assertTrue(DiceGlyphAtlasLayout.needsUnderline("6") && DiceGlyphAtlasLayout.needsUnderline("9"))
        assertTrue(!DiceGlyphAtlasLayout.needsUnderline("60"))
    }
}
