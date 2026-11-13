package com.keyserdsoze.dicethrower.ui.dice3d

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
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

    @Test
    fun percentileUnitsPaintZeroThroughNineWithoutChangingTheD10Body() {
        val standard = DiceMeshFactory.create(10)
        val units = DiceMeshFactory.create(10, percentileUnits = true)
        assertArrayEquals(standard.body, units.body, 0f)
        assertEquals(standard.glyphVertexCount, units.glyphVertexCount)
        DieGeometries.forSides(10).glyphs.forEachIndexed { index, glyph ->
            val label = (glyph.label.toInt() - 1).toString()
            val uv = DiceGlyphAtlasLayout.uvRect(label)
            val expected = floatArrayOf(uv[0], uv[3], uv[2], uv[3], uv[2], uv[1],
                uv[0], uv[3], uv[2], uv[1], uv[0], uv[1])
            val actual = FloatArray(12) { coordinate ->
                units.glyphs[(index * 6 + coordinate / 2) * DiceRenderMesh.GLYPH_STRIDE + 6 + coordinate % 2]
            }
            assertArrayEquals("percentile glyph $label", expected, actual, 0f)
            for (vertex in 0 until 6) for (component in 0 until 6) {
                val offset = (index * 6 + vertex) * DiceRenderMesh.GLYPH_STRIDE + component
                assertEquals("unchanged glyph position/normal", standard.glyphs[offset], units.glyphs[offset], 0f)
            }
        }
        assertTrue("standalone d10 still has 10", "10" in DieGeometries.forSides(10).anchorLabels)
    }

    @Test
    fun oppositePercentileUnitFacesSumToNine() {
        val geometry = DieGeometries.forSides(10)
        val units = DiceMeshFactory.create(10, percentileUnits = true)
        val paintedLabels = geometry.glyphs.indices.map { index ->
            val offset = index * 6 * DiceRenderMesh.GLYPH_STRIDE
            val u = units.glyphs[offset + 6]
            val v = units.glyphs[offset + 7]
            DiceGlyphAtlasLayout.labels.single { label ->
                val rectangle = DiceGlyphAtlasLayout.uvRect(label)
                rectangle[0] == u && rectangle[3] == v
            }.toInt()
        }
        assertEquals((0..9).toSet(), paintedLabels.toSet())
        geometry.glyphs.forEachIndexed { index, glyph ->
            val opposite = geometry.glyphs.indexOfFirst { it.normal.dot(glyph.normal) < -0.999f }
            assertTrue(opposite >= 0)
            assertEquals(9, paintedLabels[index] + paintedLabels[opposite])
        }
    }

    @Test
    fun standaloneAndPercentileD10MeshesUseIndependentCacheEntries() {
        val standard = DiceMeshFactory.create(10)
        val units = DiceMeshFactory.create(10, percentileUnits = true)
        assertNotSame(standard, units)
        assertSame(standard, DiceMeshFactory.create(10))
        assertSame(units, DiceMeshFactory.create(10, percentileUnits = true))
    }

    @Test
    fun zeroHasItsOwnAtlasCellDistinctFromTenAndDoubleZero() {
        assertTrue("0" in DiceGlyphAtlasLayout.labels)
        assertTrue(DiceGlyphAtlasLayout.cellOf("0") != DiceGlyphAtlasLayout.cellOf("10"))
        assertTrue(DiceGlyphAtlasLayout.cellOf("0") != DiceGlyphAtlasLayout.cellOf("00"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun percentileUnitsCannotBeRequestedForOtherDieBodies() {
        DiceMeshFactory.create(6, percentileUnits = true)
    }
}
