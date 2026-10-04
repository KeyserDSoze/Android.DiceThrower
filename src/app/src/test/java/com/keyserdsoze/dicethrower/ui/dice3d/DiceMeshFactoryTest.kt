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
            assertEquals(mesh.positions.size, mesh.normals.size)
        }
    }

    @Test
    fun canonicalPolyhedraHaveExpectedTriangleCounts() {
        assertEquals(4 * 3, DiceMeshFactory.create(4).vertexCount)
        assertEquals(12 * 3, DiceMeshFactory.create(6).vertexCount)
        assertEquals(8 * 3, DiceMeshFactory.create(8).vertexCount)
        assertEquals(10 * 3, DiceMeshFactory.create(10).vertexCount)
        assertEquals(12 * 3, DiceMeshFactory.create(12).vertexCount)
        assertEquals(20 * 3, DiceMeshFactory.create(20).vertexCount)
    }

    @Test
    fun generatedNormalsAreUnitLength() {
        val mesh = DiceMeshFactory.create(20)
        mesh.normals.toList().chunked(3).forEach { normal ->
            val length = sqrt(
                normal[0] * normal[0] +
                    normal[1] * normal[1] +
                    normal[2] * normal[2],
            )
            assertTrue(abs(length - 1f) < 0.001f)
        }
    }
}
