package com.keyserdsoze.dicethrower.ui.dice3d

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.sin

data class DiceMesh(
    val positions: FloatArray,
    val normals: FloatArray,
    /** Triangle geometry for face numerals, slightly lifted to avoid z-fighting. */
    val numberPositions: FloatArray,
    val numberNormals: FloatArray,
) {
    val vertexCount: Int get() = positions.size / 3
    val numberVertexCount: Int get() = numberPositions.size / 3
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
                10 -> pentagonalTrapezohedronMesh()
                12 -> dodecahedronMesh()
                20 -> icosahedronMesh()
                100 -> percentileMesh()
                else -> error("Unreachable")
            }
        }
    }

    private fun coinMesh(): DiceMesh {
        val segments = 20
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
        return buildMesh(vertices, faces, labelFaceIndices = listOf(0, 1), labels = listOf("1", "2"))
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
        return buildMesh(vertices, faces, labelFaceIndices = listOf(2, 3, 4), labels = listOf("1", "2", "3"))
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
        return buildMesh(vertices, faces, labels = (1..4).map { it.toString() })
    }

    private fun cubeMesh(): DiceMesh {
        val vertices = listOf(
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
        return buildMesh(vertices, faces, labels = (1..6).map { it.toString() })
    }

    private fun octahedronMesh(): DiceMesh {
        val vertices = listOf(
            Vec3(1f, 0f, 0f), Vec3(-1f, 0f, 0f),
            Vec3(0f, 1f, 0f), Vec3(0f, -1f, 0f),
            Vec3(0f, 0f, 1f), Vec3(0f, 0f, -1f),
        )
        val faces = listOf(
            listOf(2, 0, 4), listOf(2, 4, 1), listOf(2, 1, 5), listOf(2, 5, 0),
            listOf(3, 4, 0), listOf(3, 1, 4), listOf(3, 5, 1), listOf(3, 0, 5),
        )
        return buildMesh(vertices, faces, labels = (1..8).map { it.toString() })
    }

    /**
     * A d10 is visually modelled as a pentagonal trapezohedron: two poles and ten
     * alternating ring vertices create ten kite faces. The same shape is used for
     * d100 because percentile dice are conventionally represented by a d10 form.
     */
    private fun pentagonalTrapezohedronMesh(): DiceMesh {
        val halfHeight = 1.16f
        val ringRadius = 0.94f
        // This ratio makes each top/bottom kite approximately planar for a 36° ring step.
        val ringHeight = halfHeight * 0.10557281f
        val vertices = mutableListOf(
            Vec3(0f, halfHeight, 0f),
            Vec3(0f, -halfHeight, 0f),
        )

        repeat(10) { i ->
            val angle = (2.0 * PI * i / 10.0) - PI / 2.0
            vertices += Vec3(
                (cos(angle) * ringRadius).toFloat(),
                if (i % 2 == 0) ringHeight else -ringHeight,
                (sin(angle) * ringRadius).toFloat(),
            )
        }

        val faces = mutableListOf<List<Int>>()
        repeat(5) { i ->
            val high = 2 + (2 * i) % 10
            val low = 2 + (2 * i + 1) % 10
            val highNext = 2 + (2 * i + 2) % 10
            val lowNext = 2 + (2 * i + 3) % 10
            faces += listOf(0, high, low, highNext)
            faces += listOf(1, lowNext, highNext, low)
        }

        return buildMesh(vertices.normalizeRadius(), faces)
    }

    private fun percentileMesh(): DiceMesh {
        val halfHeight = 1.16f
        val ringRadius = 0.94f
        val ringHeight = halfHeight * 0.10557281f
        val vertices = mutableListOf(Vec3(0f, halfHeight, 0f), Vec3(0f, -halfHeight, 0f))
        repeat(10) { i ->
            val angle = (2.0 * PI * i / 10.0) - PI / 2.0
            vertices += Vec3(
                (cos(angle) * ringRadius).toFloat(),
                if (i % 2 == 0) ringHeight else -ringHeight,
                (sin(angle) * ringRadius).toFloat(),
            )
        }
        val faces = mutableListOf<List<Int>>()
        repeat(5) { i ->
            val high = 2 + (2 * i) % 10
            val low = 2 + (2 * i + 1) % 10
            val highNext = 2 + (2 * i + 2) % 10
            val lowNext = 2 + (2 * i + 3) % 10
            faces += listOf(0, high, low, highNext)
            faces += listOf(1, lowNext, highNext, low)
        }
        return buildMesh(
            vertices.normalizeRadius(),
            faces,
            labels = listOf("00", "10", "20", "30", "40", "50", "60", "70", "80", "90"),
        )
    }

    /**
     * Builds a true twelve-faced dodecahedron as the dual of the icosahedron.
     * Every triangular icosahedron face becomes a dodecahedron vertex, while
     * the five faces around each icosahedron vertex become one pentagonal face.
     */
    private fun dodecahedronMesh(): DiceMesh {
        val (icosaVertices, icosaFaces) = icosahedronGeometry()
        val dualVertices = icosaFaces.map { face ->
            val center = face
                .map(icosaVertices::get)
                .reduce(Vec3::plus) * (1f / face.size)
            center.normalized()
        }

        val pentagons = icosaVertices.indices.map { vertexIndex ->
            val adjacentFaces = icosaFaces.indices.filter { faceIndex ->
                vertexIndex in icosaFaces[faceIndex]
            }
            require(adjacentFaces.size == 5)

            val axis = icosaVertices[vertexIndex].normalized()
            val reference = if (abs(axis.y) < 0.9f) Vec3(0f, 1f, 0f) else Vec3(1f, 0f, 0f)
            val tangent = reference.cross(axis).normalized()
            val bitangent = axis.cross(tangent).normalized()

            adjacentFaces.sortedBy { faceIndex ->
                val point = dualVertices[faceIndex]
                atan2(point.dot(bitangent).toDouble(), point.dot(tangent).toDouble())
            }
        }

        return buildMesh(dualVertices.normalizeRadius(), pentagons, labels = (1..12).map { it.toString() })
    }

    private fun icosahedronMesh(): DiceMesh {
        val (vertices, faces) = icosahedronGeometry()
        return buildMesh(vertices, faces, labels = (1..20).map { it.toString() })
    }

    private fun icosahedronGeometry(): Pair<List<Vec3>, List<List<Int>>> {
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
        return vertices to faces
    }

    private fun buildMesh(
        vertices: List<Vec3>,
        faces: List<List<Int>>,
        labelFaceIndices: List<Int> = faces.indices.toList(),
        labels: List<String> = labelFaceIndices.indices.map { (it + 1).toString() },
    ): DiceMesh {
        val positions = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val numberPositions = mutableListOf<Float>()
        val numberNormals = mutableListOf<Float>()

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

        labelFaceIndices.zip(labels).forEach { (faceIndex, label) ->
            appendFaceNumber(faces[faceIndex].map(vertices::get), label, numberPositions, numberNormals)
        }

        return DiceMesh(
            positions = positions.toFloatArray(),
            normals = normals.toFloatArray(),
            numberPositions = numberPositions.toFloatArray(),
            numberNormals = numberNormals.toFloatArray(),
        )
    }

    private fun appendFaceNumber(
        polygon: List<Vec3>,
        label: String,
        positions: MutableList<Float>,
        normals: MutableList<Float>,
    ) {
        val center = polygon.reduce(Vec3::plus) * (1f / polygon.size)
        var normal = (polygon[1] - polygon[0]).cross(polygon[2] - polygon[0]).normalized()
        if (normal.dot(center) < 0f) normal = normal * -1f
        val tangent = (polygon[0] - center).normalized()
        val bitangent = normal.cross(tangent).normalized()
        val faceRadius = polygon.minOf { point -> sqrt((point - center).dot(point - center)) }
        val digitScale = faceRadius * if (label.length == 1) 0.72f else 0.48f
        val totalWidth = label.length * 0.72f * digitScale
        val startX = -(totalWidth - 0.72f * digitScale) / 2f

        label.forEachIndexed { digitIndex, character ->
            val digit = character.digitToIntOrNull() ?: return@forEachIndexed
            val originX = startX + digitIndex * 0.72f * digitScale
            DIGIT_SEGMENTS.getValue(digit).forEach { segmentIndex ->
                val segment = SEGMENTS[segmentIndex]
                appendQuad(
                    center = center + tangent * (originX + segment.x * digitScale) +
                        bitangent * (segment.y * digitScale) + normal * 0.018f,
                    tangent = tangent,
                    bitangent = bitangent,
                    normal = normal,
                    halfWidth = segment.width * digitScale * 0.5f,
                    halfHeight = segment.height * digitScale * 0.5f,
                    positions = positions,
                    normals = normals,
                )
            }
        }
    }

    private fun appendQuad(
        center: Vec3,
        tangent: Vec3,
        bitangent: Vec3,
        normal: Vec3,
        halfWidth: Float,
        halfHeight: Float,
        positions: MutableList<Float>,
        normals: MutableList<Float>,
    ) {
        val a = center - tangent * halfWidth - bitangent * halfHeight
        val b = center + tangent * halfWidth - bitangent * halfHeight
        val c = center + tangent * halfWidth + bitangent * halfHeight
        val d = center - tangent * halfWidth + bitangent * halfHeight
        listOf(a, b, c, a, c, d).forEach { point ->
            positions += point.x
            positions += point.y
            positions += point.z
            normals += normal.x
            normals += normal.y
            normals += normal.z
        }
    }

    private fun List<Vec3>.normalizeRadius(): List<Vec3> {
        val maxLength = maxOf { sqrt(it.dot(it)) }
        return map { it * (1f / maxLength) }
    }

    private data class Segment(val x: Float, val y: Float, val width: Float, val height: Float)

    private val SEGMENTS = listOf(
        Segment(0f, 0.78f, 0.52f, 0.13f), Segment(0.29f, 0.39f, 0.13f, 0.62f),
        Segment(0.29f, -0.39f, 0.13f, 0.62f), Segment(0f, -0.78f, 0.52f, 0.13f),
        Segment(-0.29f, -0.39f, 0.13f, 0.62f), Segment(-0.29f, 0.39f, 0.13f, 0.62f),
        Segment(0f, 0f, 0.52f, 0.13f),
    )

    private val DIGIT_SEGMENTS = mapOf(
        0 to listOf(0, 1, 2, 3, 4, 5), 1 to listOf(1, 2), 2 to listOf(0, 1, 6, 4, 3),
        3 to listOf(0, 1, 6, 2, 3), 4 to listOf(5, 6, 1, 2), 5 to listOf(0, 5, 6, 2, 3),
        6 to listOf(0, 5, 6, 4, 2, 3), 7 to listOf(0, 1, 2),
        8 to listOf(0, 1, 2, 3, 4, 5, 6), 9 to listOf(0, 1, 2, 3, 5, 6),
    )
}
