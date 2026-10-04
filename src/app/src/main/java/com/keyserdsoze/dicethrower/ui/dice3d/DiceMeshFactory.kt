package com.keyserdsoze.dicethrower.ui.dice3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.sin

data class DiceMesh(
    val positions: FloatArray,
    val normals: FloatArray,
) {
    val vertexCount: Int get() = positions.size / 3
}

internal data class Vec3(
    val x: Float,
    val y: Float,
    val z: Float,
) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(value: Float) = Vec3(x * value, y * value, z * value)

    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vec3): Vec3 = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x,
    )

    fun normalized(): Vec3 {
        val length = sqrt(max(0.000001f, dot(this)))
        return Vec3(x / length, y / length, z / length)
    }
}

object DiceMeshFactory {
    private val supportedSides = setOf(2, 3, 4, 6, 8, 10, 12, 20, 100)
    private val cache = mutableMapOf<Int, DiceMesh>()

    fun create(sides: Int): DiceMesh {
        require(sides in supportedSides) { "Unsupported 3D die d$sides" }
        return cache.getOrPut(sides) {
            when (sides) {
                2 -> coinMesh()
                3 -> triangularPrismMesh()
                4 -> tetrahedronMesh()
                6 -> cubeMesh()
                8 -> octahedronMesh()
                10 -> bipyramidMesh(5, radius = 0.93f, halfHeight = 1.15f)
                12 -> bipyramidMesh(6, radius = 0.95f, halfHeight = 1.05f)
                20 -> icosahedronMesh()
                100 -> bipyramidMesh(5, radius = 1.0f, halfHeight = 1.20f)
                else -> error("Unreachable")
            }
        }
    }

    private fun coinMesh(): DiceMesh {
        val segments = 16
        val halfHeight = 0.20f
        val radius = 1.0f
        val vertices = mutableListOf<Vec3>()

        repeat(segments) { i ->
            val angle = 2.0 * PI * i / segments
            vertices += Vec3(
                (cos(angle) * radius).toFloat(),
                halfHeight,
                (sin(angle) * radius).toFloat(),
            )
        }
        repeat(segments) { i ->
            val angle = 2.0 * PI * i / segments
            vertices += Vec3(
                (cos(angle) * radius).toFloat(),
                -halfHeight,
                (sin(angle) * radius).toFloat(),
            )
        }

        val faces = mutableListOf<List<Int>>()
        faces += (0 until segments).toList()
        faces += (segments until segments * 2).toList().reversed()
        repeat(segments) { i ->
            val next = (i + 1) % segments
            faces += listOf(i, next, segments + next, segments + i)
        }
        return buildMesh(vertices, faces)
    }

    private fun triangularPrismMesh(): DiceMesh {
        val h = 0.78f
        val top = listOf(
            Vec3(0f, h, 1f),
            Vec3(-0.866f, h, -0.5f),
            Vec3(0.866f, h, -0.5f),
        )
        val bottom = top.map { it.copy(y = -h) }
        val vertices = top + bottom
        val faces = listOf(
            listOf(0, 2, 1),
            listOf(3, 4, 5),
            listOf(0, 1, 4, 3),
            listOf(1, 2, 5, 4),
            listOf(2, 0, 3, 5),
        )
        return buildMesh(vertices, faces)
    }

    private fun tetrahedronMesh(): DiceMesh {
        val vertices = listOf(
            Vec3(1f, 1f, 1f),
            Vec3(-1f, -1f, 1f),
            Vec3(-1f, 1f, -1f),
            Vec3(1f, -1f, -1f),
        ).normalizeRadius()
        val faces = listOf(
            listOf(0, 1, 2),
            listOf(0, 3, 1),
            listOf(0, 2, 3),
            listOf(1, 3, 2),
        )
        return buildMesh(vertices, faces)
    }

    private fun cubeMesh(): DiceMesh {
        val v = listOf(
            Vec3(-1f, -1f, -1f), Vec3(1f, -1f, -1f),
            Vec3(1f, 1f, -1f), Vec3(-1f, 1f, -1f),
            Vec3(-1f, -1f, 1f), Vec3(1f, -1f, 1f),
            Vec3(1f, 1f, 1f), Vec3(-1f, 1f, 1f),
        ).normalizeRadius()
        val faces = listOf(
            listOf(0, 3, 2, 1),
            listOf(4, 5, 6, 7),
            listOf(0, 4, 7, 3),
            listOf(1, 2, 6, 5),
            listOf(3, 7, 6, 2),
            listOf(0, 1, 5, 4),
        )
        return buildMesh(v, faces)
    }

    private fun octahedronMesh(): DiceMesh {
        val v = listOf(
            Vec3(1f, 0f, 0f), Vec3(-1f, 0f, 0f),
            Vec3(0f, 1f, 0f), Vec3(0f, -1f, 0f),
            Vec3(0f, 0f, 1f), Vec3(0f, 0f, -1f),
        )
        val faces = listOf(
            listOf(2, 0, 4), listOf(2, 4, 1), listOf(2, 1, 5), listOf(2, 5, 0),
            listOf(3, 4, 0), listOf(3, 1, 4), listOf(3, 5, 1), listOf(3, 0, 5),
        )
        return buildMesh(v, faces)
    }

    private fun bipyramidMesh(
        equatorCount: Int,
        radius: Float,
        halfHeight: Float,
    ): DiceMesh {
        val vertices = mutableListOf(
            Vec3(0f, halfHeight, 0f),
            Vec3(0f, -halfHeight, 0f),
        )
        repeat(equatorCount) { i ->
            val angle = (2.0 * PI * i / equatorCount) - PI / 2.0
            vertices += Vec3(
                (cos(angle) * radius).toFloat(),
                0f,
                (sin(angle) * radius).toFloat(),
            )
        }
        val faces = mutableListOf<List<Int>>()
        repeat(equatorCount) { i ->
            val current = 2 + i
            val next = 2 + ((i + 1) % equatorCount)
            faces += listOf(0, current, next)
            faces += listOf(1, next, current)
        }
        return buildMesh(vertices.normalizeRadius(), faces)
    }

    private fun icosahedronMesh(): DiceMesh {
        val phi = ((1.0 + sqrt(5.0)) / 2.0).toFloat()
        val vertices = listOf(
            Vec3(-1f, phi, 0f), Vec3(1f, phi, 0f), Vec3(-1f, -phi, 0f), Vec3(1f, -phi, 0f),
            Vec3(0f, -1f, phi), Vec3(0f, 1f, phi), Vec3(0f, -1f, -phi), Vec3(0f, 1f, -phi),
            Vec3(phi, 0f, -1f), Vec3(phi, 0f, 1f), Vec3(-phi, 0f, -1f), Vec3(-phi, 0f, 1f),
        ).normalizeRadius()
        val faces = listOf(
            listOf(0, 11, 5), listOf(0, 5, 1), listOf(0, 1, 7), listOf(0, 7, 10), listOf(0, 10, 11),
            listOf(1, 5, 9), listOf(5, 11, 4), listOf(11, 10, 2), listOf(10, 7, 6), listOf(7, 1, 8),
            listOf(3, 9, 4), listOf(3, 4, 2), listOf(3, 2, 6), listOf(3, 6, 8), listOf(3, 8, 9),
            listOf(4, 9, 5), listOf(2, 4, 11), listOf(6, 2, 10), listOf(8, 6, 7), listOf(9, 8, 1),
        )
        return buildMesh(vertices, faces)
    }

    private fun buildMesh(
        vertices: List<Vec3>,
        faces: List<List<Int>>,
    ): DiceMesh {
        val positions = mutableListOf<Float>()
        val normals = mutableListOf<Float>()

        faces.forEach { polygon ->
            require(polygon.size >= 3)
            for (i in 1 until polygon.lastIndex) {
                var a = vertices[polygon[0]]
                var b = vertices[polygon[i]]
                var c = vertices[polygon[i + 1]]
                var normal = (b - a).cross(c - a).normalized()
                val centroid = (a + b + c) * (1f / 3f)
                if (normal.dot(centroid) < 0f) {
                    val temp = b
                    b = c
                    c = temp
                    normal = (b - a).cross(c - a).normalized()
                }
                listOf(a, b, c).forEach { point ->
                    positions += point.x
                    positions += point.y
                    positions += point.z
                    normals += normal.x
                    normals += normal.y
                    normals += normal.z
                }
            }
        }

        return DiceMesh(
            positions = positions.toFloatArray(),
            normals = normals.toFloatArray(),
        )
    }

    private fun List<Vec3>.normalizeRadius(): List<Vec3> {
        val maxLength = maxOf { sqrt(it.dot(it)) }
        return map { it * (1f / maxLength) }
    }
}
