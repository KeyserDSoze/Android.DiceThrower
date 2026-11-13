package com.keyserdsoze.dicethrower.ui.dice3d

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A numeral quad lying on a die face, in unit (circumradius 1) body space. */
internal data class GlyphPlacement(
    val center: Vec3,
    val right: Vec3,
    val up: Vec3,
    val normal: Vec3,
    val size: Float,
    val label: String,
)

/**
 * Exact convex hull, value anchors and symmetry group of one die type.
 *
 * An anchor is the body-space direction that points up when its label is the visible result:
 * a face normal for most dice, the top vertex for a d4. The rotation group is used to remap a
 * naturally simulated landing onto the value already chosen by the dice engine, so the motion
 * stays physical while the logical result is never influenced by the renderer.
 */
internal class DieGeometry(
    val sides: Int,
    val vertices: List<Vec3>,
    faces: List<List<Int>>,
    val anchors: List<Vec3>,
    val anchorLabels: List<String>,
    glyphs: (DieGeometry) -> List<GlyphPlacement>,
    val bevelRadius: Float,
) {
    /** Faces with outward counter-clockwise winding. */
    val faces: List<List<Int>> = faces.map { face ->
        val normal = rawNormal(face)
        val centroid = face.map(vertices::get).reduce(Vec3::plus) * (1f / face.size)
        if (normal.dot(centroid) < 0f) face.reversed() else face
    }
    val faceNormals: List<Vec3> = this.faces.map { rawNormal(it).normalized() }
    val faceOffsets: List<Float> = this.faces.mapIndexed { index, face -> faceNormals[index].dot(vertices[face[0]]) }
    val circumradius: Float = vertices.maxOf { it.length() }
    val inradius: Float = faceOffsets.min()
    val glyphs: List<GlyphPlacement> = glyphs(this)
    val symmetries: List<Quat> by lazy { computeSymmetries() }

    /** Principal moments of inertia per unit mass for the unit-size solid. */
    val inertiaDiagonal: Vec3 by lazy { computeInertia() }

    fun labelForValue(value: Int): String = when (sides) {
        100 -> if (value >= 100 || value < 10) "00" else "${value / 10}0"
        else -> value.toString()
    }

    fun anchorIndicesForValue(value: Int): List<Int> {
        val label = labelForValue(value)
        return anchorLabels.indices.filter { anchorLabels[it] == label }.ifEmpty { listOf(0) }
    }

    /** Index of the anchor pointing most upwards (+Z) for a body rotation. */
    fun upAnchorIndex(rotation: Quat): Int = anchors.indices.maxBy { rotation.rotate(anchors[it]).z }

    /**
     * Body-space symmetry S such that, at the final rotation R, R·S shows [value] on top.
     * Among the symmetries that achieve it (one per rotational symmetry of the face) the one
     * leaving the top numeral most upright on screen (+Y) is chosen, so results read naturally.
     */
    fun remapFor(value: Int, finalRotation: Quat): Quat {
        val landed = upAnchorIndex(finalRotation)
        val target = anchors[landed]
        var best = Quat.IDENTITY
        var bestScore = -Float.MAX_VALUE
        symmetries.forEach { symmetry ->
            anchorIndicesForValue(value).forEach { index ->
                if (symmetry.rotate(anchors[index]).dot(target) < 0.999f) return@forEach
                val glyph = glyphs.firstOrNull { it.normal.dot(anchors[index]) > 0.999f }
                val score = glyph?.let { (finalRotation * symmetry).rotate(it.up).y } ?: 0f
                if (score > bestScore) {
                    bestScore = score
                    best = symmetry
                }
            }
        }
        return best
    }

    private fun rawNormal(face: List<Int>): Vec3 {
        // Newell's method is robust for every planar convex polygon.
        var nx = 0f
        var ny = 0f
        var nz = 0f
        face.indices.forEach { i ->
            val a = vertices[face[i]]
            val b = vertices[face[(i + 1) % face.size]]
            nx += (a.y - b.y) * (a.z + b.z)
            ny += (a.z - b.z) * (a.x + b.x)
            nz += (a.x - b.x) * (a.y + b.y)
        }
        return Vec3(nx, ny, nz)
    }

    private fun faceFrame(faceIndex: Int, vertexIndex: Int): Quat {
        val face = faces[faceIndex]
        val normal = faceNormals[faceIndex]
        val centroid = face.map(vertices::get).reduce(Vec3::plus) * (1f / face.size)
        val toVertex = vertices[vertexIndex] - centroid
        val tangent = (toVertex - normal * toVertex.dot(normal)).normalized()
        return Quat.fromBasis(tangent, normal.cross(tangent), normal)
    }

    private fun computeSymmetries(): List<Quat> {
        val referenceFace = 0
        val reference = faceFrame(referenceFace, faces[referenceFace][0]).conjugate()
        val result = mutableListOf<Quat>()
        faces.indices.filter { faces[it].size == faces[referenceFace].size }.forEach { faceIndex ->
            faces[faceIndex].forEach { vertexIndex ->
                val candidate = (faceFrame(faceIndex, vertexIndex) * reference).normalized()
                val preservesHull = vertices.all { vertex ->
                    val mapped = candidate.rotate(vertex)
                    vertices.any { other -> (other - mapped).length() < 2e-3f }
                }
                if (preservesHull && result.none { abs(it.dot(candidate)) > 0.99999f }) result += candidate
            }
        }
        return result
    }

    private fun computeInertia(): Vec3 {
        var volume = 0.0
        val second = DoubleArray(9)
        faces.forEach { face ->
            for (i in 1 until face.lastIndex) {
                val a = vertices[face[0]]
                val b = vertices[face[i]]
                val c = vertices[face[i + 1]]
                val det = a.dot(b.cross(c)).toDouble()
                val tetraVolume = det / 6.0
                volume += tetraVolume
                val sx = (a.x + b.x + c.x).toDouble()
                val sy = (a.y + b.y + c.y).toDouble()
                val sz = (a.z + b.z + c.z).toDouble()
                val points = listOf(a, b, c)
                val s = doubleArrayOf(sx, sy, sz)
                for (row in 0..2) for (column in 0..2) {
                    var sum = s[row] * s[column]
                    points.forEach { point ->
                        val p = doubleArrayOf(point.x.toDouble(), point.y.toDouble(), point.z.toDouble())
                        sum += p[row] * p[column]
                    }
                    second[row * 3 + column] += tetraVolume / 20.0 * sum
                }
            }
        }
        val trace = second[0] + second[4] + second[8]
        return Vec3(
            ((trace - second[0]) / volume).toFloat(),
            ((trace - second[4]) / volume).toFloat(),
            ((trace - second[8]) / volume).toFloat(),
        )
    }
}

internal object DieGeometries {
    private val supportedSides = setOf(2, 3, 4, 6, 8, 10, 12, 20, 100)
    private val cache = mutableMapOf<Int, DieGeometry>()

    fun isSupported(sides: Int): Boolean = sides in supportedSides

    fun forSides(sides: Int): DieGeometry {
        require(sides in supportedSides) { "Unsupported 3D die d$sides" }
        return synchronized(cache) {
            cache.getOrPut(sides) {
                when (sides) {
                    2 -> coin()
                    3 -> cube(sides = 3, pairs = listOf("1" to "3", "2" to "2", "3" to "1"))
                    4 -> tetrahedron()
                    6 -> cube(sides = 6, pairs = sumPairs(6))
                    8 -> octahedron()
                    10 -> trapezohedron(10, top = listOf("1", "5", "9", "3", "7")) { (11 - it.toInt()).toString() }
                    12 -> dodecahedron()
                    20 -> icosahedron()
                    100 -> trapezohedron(100, top = listOf("00", "40", "80", "20", "60")) {
                        (90 - it.toInt()).toString().padStart(2, '0')
                    }
                    else -> error("Unreachable")
                }
            }
        }
    }

    private fun sumPairs(sides: Int): List<Pair<String, String>> =
        (1..sides / 2).map { it.toString() to (sides + 1 - it).toString() }

    private fun List<Vec3>.normalizeRadius(): List<Vec3> {
        val maxLength = maxOf { it.length() }
        return map { it * (1f / maxLength) }
    }

    private fun coin(): DieGeometry {
        val segments = 32
        val halfHeight = 0.19f
        val ring = { y: Float ->
            List(segments) { i ->
                val angle = 2.0 * PI * i / segments
                Vec3(cos(angle).toFloat(), y, sin(angle).toFloat())
            }
        }
        val vertices = (ring(halfHeight) + ring(-halfHeight)).normalizeRadius()
        val faces = buildList {
            add((0 until segments).toList())
            add((segments until segments * 2).toList().reversed())
            repeat(segments) { i ->
                val next = (i + 1) % segments
                add(listOf(i, next, segments + next, segments + i))
            }
        }
        val labels = listOf("1", "2") + List(segments) { null }
        return faceNumbered(2, vertices, faces, labels, upMode = UpMode.VERTEX, fill = 0.60f, bevel = 0.045f)
    }

    private fun cube(sides: Int, pairs: List<Pair<String, String>>): DieGeometry {
        val vertices = listOf(
            Vec3(-1f, -1f, -1f), Vec3(1f, -1f, -1f),
            Vec3(1f, 1f, -1f), Vec3(-1f, 1f, -1f),
            Vec3(-1f, -1f, 1f), Vec3(1f, -1f, 1f),
            Vec3(1f, 1f, 1f), Vec3(-1f, 1f, 1f),
        ).normalizeRadius()
        val faces = listOf(
            listOf(0, 3, 2, 1), listOf(4, 5, 6, 7), listOf(0, 4, 7, 3),
            listOf(1, 2, 6, 5), listOf(3, 7, 6, 2), listOf(0, 1, 5, 4),
        )
        return faceNumbered(
            sides, vertices, faces, oppositeLabels(vertices, faces, pairs),
            upMode = UpMode.EDGE, fill = 0.86f, bevel = 0.095f,
        )
    }

    private fun octahedron(): DieGeometry {
        val vertices = listOf(
            Vec3(1f, 0f, 0f), Vec3(-1f, 0f, 0f),
            Vec3(0f, 1f, 0f), Vec3(0f, -1f, 0f),
            Vec3(0f, 0f, 1f), Vec3(0f, 0f, -1f),
        )
        val faces = listOf(
            listOf(2, 0, 4), listOf(2, 4, 1), listOf(2, 1, 5), listOf(2, 5, 0),
            listOf(3, 4, 0), listOf(3, 1, 4), listOf(3, 5, 1), listOf(3, 0, 5),
        )
        return faceNumbered(
            8, vertices, faces, oppositeLabels(vertices, faces, sumPairs(8)),
            upMode = UpMode.VERTEX, fill = 0.84f, bevel = 0.072f,
        )
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

    private fun icosahedron(): DieGeometry {
        val (vertices, faces) = icosahedronGeometry()
        return faceNumbered(
            20, vertices, faces, oppositeLabels(vertices, faces, sumPairs(20)),
            upMode = UpMode.VERTEX, fill = 0.80f, bevel = 0.065f,
        )
    }

    /** True dodecahedron built as the dual of the icosahedron. */
    private fun dodecahedron(): DieGeometry {
        val (icosaVertices, icosaFaces) = icosahedronGeometry()
        val dualVertices = icosaFaces.map { face ->
            (face.map(icosaVertices::get).reduce(Vec3::plus) * (1f / face.size)).normalized()
        }
        val pentagons = icosaVertices.indices.map { vertexIndex ->
            val adjacent = icosaFaces.indices.filter { vertexIndex in icosaFaces[it] }
            val axis = icosaVertices[vertexIndex].normalized()
            val reference = if (abs(axis.y) < 0.9f) Vec3.UNIT_Y else Vec3.UNIT_X
            val tangent = reference.cross(axis).normalized()
            val bitangent = axis.cross(tangent).normalized()
            adjacent.sortedBy { faceIndex ->
                val point = dualVertices[faceIndex]
                atan2(point.dot(bitangent).toDouble(), point.dot(tangent).toDouble())
            }
        }
        val vertices = dualVertices.normalizeRadius()
        return faceNumbered(
            12, vertices, pentagons, oppositeLabels(vertices, pentagons, sumPairs(12)),
            upMode = UpMode.VERTEX, fill = 0.80f, bevel = 0.065f,
        )
    }

    /** Pentagonal trapezohedron (d10/d100): two poles and ten alternating ring vertices. */
    private fun trapezohedron(sides: Int, top: List<String>, opposite: (String) -> String): DieGeometry {
        val halfHeight = 1.16f
        val ringRadius = 0.94f
        // z = H(1 - cos36°)/(1 + cos36°) makes every kite exactly planar.
        val ringHeight = halfHeight * 0.10557281f
        val raw = mutableListOf(Vec3(0f, halfHeight, 0f), Vec3(0f, -halfHeight, 0f))
        repeat(10) { i ->
            val angle = (2.0 * PI * i / 10.0) - PI / 2.0
            raw += Vec3(
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
        val vertices = raw.normalizeRadius()
        val normals = faces.map { face -> faceNormalOf(vertices, face) }
        val labels = arrayOfNulls<String>(faces.size)
        val topFaces = faces.indices.filter { 0 in faces[it] }.sortedBy { index ->
            atan2(normals[index].z.toDouble(), normals[index].x.toDouble())
        }
        topFaces.forEachIndexed { order, faceIndex ->
            labels[faceIndex] = top[order]
            val oppositeFace = faces.indices.minBy { normals[it].dot(normals[faceIndex]) }
            labels[oppositeFace] = opposite(top[order])
        }
        return faceNumbered(
            sides, vertices, faces, labels.toList(),
            upMode = UpMode.VERTEX, fill = if (sides == 100) 0.92f else 0.88f, bevel = 0.06f,
        )
    }

    /** d4 read at the top vertex: every face shows the value of each of its three corners. */
    private fun tetrahedron(): DieGeometry {
        val vertices = listOf(
            Vec3(1f, 1f, 1f), Vec3(-1f, -1f, 1f),
            Vec3(-1f, 1f, -1f), Vec3(1f, -1f, -1f),
        ).normalizeRadius()
        val faces = listOf(listOf(0, 1, 2), listOf(0, 3, 1), listOf(0, 2, 3), listOf(1, 3, 2))
        val vertexLabels = listOf("1", "2", "3", "4")
        return DieGeometry(
            sides = 4,
            vertices = vertices,
            faces = faces,
            anchors = vertices.map(Vec3::normalized),
            anchorLabels = vertexLabels,
            glyphs = { geometry ->
                geometry.faces.flatMapIndexed { faceIndex, face ->
                    val normal = geometry.faceNormals[faceIndex]
                    val points = face.map(vertices::get)
                    val centroid = points.reduce(Vec3::plus) * (1f / 3f)
                    val apothem = (points[0] + points[1]) * 0.5f - centroid
                    face.map { vertexIndex ->
                        val up = (vertices[vertexIndex] - centroid).normalized()
                        GlyphPlacement(
                            center = centroid + (vertices[vertexIndex] - centroid) * 0.50f,
                            right = up.cross(normal).normalized(),
                            up = up,
                            normal = normal,
                            size = apothem.length() * 1.02f,
                            label = vertexLabels[vertexIndex],
                        )
                    }
                }
            },
            bevelRadius = 0.07f,
        )
    }

    private enum class UpMode { VERTEX, EDGE }

    private fun faceNumbered(
        sides: Int,
        vertices: List<Vec3>,
        faces: List<List<Int>>,
        labels: List<String?>,
        upMode: UpMode,
        fill: Float,
        bevel: Float,
    ): DieGeometry {
        val labelled = faces.indices.filter { labels[it] != null }
        return DieGeometry(
            sides = sides,
            vertices = vertices,
            faces = faces,
            anchors = labelled.map { faceNormalOf(vertices, faces[it]) },
            anchorLabels = labelled.map { labels[it]!! },
            glyphs = { geometry ->
                labelled.map { faceIndex ->
                    val face = geometry.faces[faceIndex]
                    val normal = geometry.faceNormals[faceIndex]
                    // Keep the authored first vertex (pole/apex) as the reading direction even when
                    // winding normalization reversed the polygon.
                    val authored = faces[faceIndex]
                    val (center, radius) = inscribedCircle(face.map(vertices::get), normal)
                    val target = when (upMode) {
                        UpMode.VERTEX -> vertices[authored[0]]
                        UpMode.EDGE -> (vertices[authored[0]] + vertices[authored[1]]) * 0.5f
                    }
                    val toTarget = target - center
                    val up = (toTarget - normal * toTarget.dot(normal)).normalized()
                    GlyphPlacement(
                        center = center,
                        right = up.cross(normal).normalized(),
                        up = up,
                        normal = normal,
                        size = radius * 2f * fill,
                        label = labels[faceIndex]!!,
                    )
                }
            },
            bevelRadius = bevel,
        )
    }

    private fun faceNormalOf(vertices: List<Vec3>, face: List<Int>): Vec3 {
        val points = face.map(vertices::get)
        val centroid = points.reduce(Vec3::plus) * (1f / points.size)
        var normal = (points[1] - points[0]).cross(points[2] - points[0]).normalized()
        if (normal.dot(centroid) < 0f) normal = -normal
        return normal
    }

    /**
     * Pairs every face with its parallel opposite face so values on opposite sides add up like
     * real dice (1/6, 2/5, 3/4 …). Consecutive low values are spread away from each other.
     */
    private fun oppositeLabels(
        vertices: List<Vec3>,
        faces: List<List<Int>>,
        pairs: List<Pair<String, String>>,
    ): List<String?> {
        val normals = faces.map { faceNormalOf(vertices, it) }
        val labels = arrayOfNulls<String>(faces.size)
        var previous = -1
        pairs.forEach { (first, second) ->
            val candidates = faces.indices.filter { labels[it] == null }
            val chosen = if (previous < 0) candidates.first() else candidates.minBy { normals[it].dot(normals[previous]) }
            labels[chosen] = first
            val opposite = faces.indices.filter { labels[it] == null }.minBy { normals[it].dot(normals[chosen]) }
            labels[opposite] = second
            previous = chosen
        }
        return labels.toList()
    }

    /** Largest circle inside a convex face (its centre is where a numeral has the most room). */
    private fun inscribedCircle(points: List<Vec3>, normal: Vec3): Pair<Vec3, Float> {
        val centroid = points.reduce(Vec3::plus) * (1f / points.size)
        val axisU = (points[0] - centroid).let { (it - normal * it.dot(normal)).normalized() }
        val axisV = normal.cross(axisU)
        val flat = points.map { (it - centroid).let { p -> p.dot(axisU) to p.dot(axisV) } }
        fun clearance(u: Float, v: Float): Float = flat.indices.minOf { i ->
            val (ax, ay) = flat[i]
            val (bx, by) = flat[(i + 1) % flat.size]
            val ex = bx - ax
            val ey = by - ay
            (ex * (v - ay) - ey * (u - ax)) / sqrt(ex * ex + ey * ey)
        }
        var u = 0f
        var v = 0f
        var best = clearance(u, v)
        var step = flat.maxOf { sqrt(it.first * it.first + it.second * it.second) } * 0.25f
        while (step > 1e-5f) {
            var improved = false
            for (du in -1..1) for (dv in -1..1) {
                if (du == 0 && dv == 0) continue
                val candidate = clearance(u + du * step, v + dv * step)
                if (candidate > best + 1e-7f) {
                    best = candidate
                    u += du * step
                    v += dv * step
                    improved = true
                }
            }
            if (!improved) step *= 0.5f
        }
        return (centroid + axisU * u + axisV * v) to abs(best)
    }
}
