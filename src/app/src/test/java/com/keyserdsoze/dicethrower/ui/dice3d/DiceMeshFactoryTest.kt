package com.keyserdsoze.dicethrower.ui.dice3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class DiceMeshFactoryTest {
    @Test
    fun allSupportedDiceProduceTrianglesAndNormals() {
        listOf(2, 3, 4, 6, 8, 10, 12, 20, 100).forEach { sides ->
            val mesh = DiceMeshFactory.create(sides)
            assertTrue("d$sides should have vertices", mesh.vertexCount > 0)
            assertEquals(0, mesh.vertexCount % 3)
            assertTrue("d$sides should have numbered faces", mesh.numberVertexCount > 0)
            assertEquals(0, mesh.numberVertexCount % 3)
            assertEquals(mesh.positions.size, mesh.normals.size)
            assertEquals(mesh.numberPositions.size, mesh.numberNormals.size)
            val expectedNumberedFaces = if (sides == 100) 10 else sides
            assertEquals("d$sides face normals", expectedNumberedFaces * 3, mesh.valueFaceNormals.size)
        }
    }

    @Test
    fun canonicalPolyhedraHaveExpectedTriangleCounts() {
        assertEquals(4 * 3, DiceMeshFactory.create(4).vertexCount)
        assertEquals(12 * 3, DiceMeshFactory.create(6).vertexCount)
        assertEquals(8 * 3, DiceMeshFactory.create(8).vertexCount)
        // Ten quadrilateral kite faces -> 20 triangles.
        assertEquals(20 * 3, DiceMeshFactory.create(10).vertexCount)
        // Twelve pentagonal faces -> 36 triangles.
        assertEquals(36 * 3, DiceMeshFactory.create(12).vertexCount)
        assertEquals(20 * 3, DiceMeshFactory.create(20).vertexCount)
        assertEquals(20 * 3, DiceMeshFactory.create(100).vertexCount)
    }

    @Test
    fun d12IsMoreDetailedThanD20TriangleSurfaceCount() {
        // A d12 has fewer faces than a d20, but each pentagon is triangulated into
        // three triangles, so the render mesh has more triangles than the d20.
        assertTrue(DiceMeshFactory.create(12).vertexCount > DiceMeshFactory.create(20).vertexCount)
    }

    @Test
    fun generatedNormalsAreUnitLength() {
        listOf(10, 12, 20).forEach { sides ->
            val mesh = DiceMeshFactory.create(sides)
            mesh.normals.toList().chunked(3).forEach { normal ->
                val length = sqrt(
                    normal[0] * normal[0] +
                        normal[1] * normal[1] +
                        normal[2] * normal[2],
                )
                assertTrue("d$sides normal length=$length", abs(length - 1f) < 0.001f)
            }
        }
    }

    @Test
    fun numberedFaceNormalsAreUnitLength() {
        listOf(2, 3, 4, 6, 8, 10, 12, 20, 100).forEach { sides ->
            DiceMeshFactory.create(sides).valueFaceNormals.toList().chunked(3).forEach { normal ->
                val length = sqrt(normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2])
                assertTrue("d$sides numbered face normal length=$length", abs(length - 1f) < 0.001f)
            }
        }
    }
}
