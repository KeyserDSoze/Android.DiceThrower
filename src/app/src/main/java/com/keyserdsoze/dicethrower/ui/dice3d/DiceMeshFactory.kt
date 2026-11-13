package com.keyserdsoze.dicethrower.ui.dice3d

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil

/** GPU-ready interleaved geometry for one die type in unit (circumradius 1) body space. */
internal class DiceRenderMesh(
    /** position xyz + normal xyz per vertex, triangles. */
    val body: FloatArray,
    /** position xyz + normal xyz + glyph-atlas uv per vertex, triangles. */
    val glyphs: FloatArray,
) {
    val bodyVertexCount: Int get() = body.size / BODY_STRIDE
    val glyphVertexCount: Int get() = glyphs.size / GLYPH_STRIDE

    companion object {
        const val BODY_STRIDE = 6
        const val GLYPH_STRIDE = 8
    }
}

/** Cell layout of the numeral atlas shared by mesh UVs (pure) and bitmap painting (Android). */
internal object DiceGlyphAtlasLayout {
    const val COLUMNS = 6
    /** Square cells in a square power-of-two texture (mipmaps require POT on GLES2). */
    const val ROWS = 6
    val labels: List<String> = ((1..20).map(Int::toString) + (0..9).map { "${it}0" }).distinct()

    init {
        require(labels.size <= COLUMNS * ROWS)
    }

    fun cellOf(label: String): Int = labels.indexOf(label).coerceAtLeast(0)

    /** Classic dice convention: underline 6 and 9 so they cannot be confused. */
    fun needsUnderline(label: String): Boolean = label == "6" || label == "9"

    /** [u0, vTop, u1, vBottom] in texture space; bitmap row 0 is v = 0. */
    fun uvRect(label: String): FloatArray {
        val cell = cellOf(label)
        val column = cell % COLUMNS
        val row = cell / COLUMNS
        val inset = 0.5f / 1024f
        return floatArrayOf(
            column.toFloat() / COLUMNS + inset,
            row.toFloat() / ROWS + inset,
            (column + 1f) / COLUMNS - inset,
            (row + 1f) / ROWS - inset,
        )
    }
}

object DiceMeshFactory {
    private const val GLYPH_LIFT = 0.006f
    private const val MAX_ARC_STEP_RADIANS = 0.40f
    private val cache = mutableMapOf<Int, DiceRenderMesh>()

    internal fun create(sides: Int): DiceRenderMesh = synchronized(cache) {
        cache.getOrPut(sides) { build(DieGeometries.forSides(sides)) }
    }

    private fun build(geometry: DieGeometry): DiceRenderMesh {
        val body = FloatBuilder()
        appendRoundedBody(geometry, body)
        val glyphs = FloatBuilder()
        geometry.glyphs.forEach { appendGlyph(it, glyphs) }
        return DiceRenderMesh(body.toArray(), glyphs.toArray())
    }

    /**
     * Rounded die = (polyhedron shrunk by r) ⊕ sphere(r): flat faces stay on their original planes,
     * edges become cylindrical strips and corners spherical patches with continuous normals.
     */
    private fun appendRoundedBody(geometry: DieGeometry, out: FloatBuilder) {
        val radius = geometry.bevelRadius
        val normals = geometry.faceNormals
        val facesOfVertex = geometry.vertices.indices.map { vertex ->
            geometry.faces.indices.filter { vertex in geometry.faces[it] }
        }
        val inner = geometry.vertices.indices.map { vertex ->
            offsetVertex(
                planes = facesOfVertex[vertex].map { normals[it] to geometry.faceOffsets[it] - radius },
                fallback = geometry.vertices[vertex],
            )
        }

        geometry.faces.forEachIndexed { faceIndex, face ->
            val normal = normals[faceIndex]
            val points = face.map { inner[it] + normal * radius }
            for (i in 1 until points.lastIndex) {
                out.triangle(points[0], points[i], points[i + 1], normal, normal, normal)
            }
        }

        val edges = LinkedHashMap<Long, MutableList<Int>>()
        geometry.faces.forEachIndexed { faceIndex, face ->
            face.indices.forEach { k ->
                val a = face[k]
                val b = face[(k + 1) % face.size]
                edges.getOrPut(edgeKey(a, b)) { mutableListOf() } += faceIndex
            }
        }
        edges.forEach { (key, faces) ->
            if (faces.size != 2) return@forEach
            val a = (key shr 32).toInt()
            val b = (key and 0xffffffffL).toInt()
            val first = minOf(faces[0], faces[1])
            val second = maxOf(faces[0], faces[1])
            val steps = arcSteps(normals[first], normals[second])
            for (s in 0 until steps) {
                val n0 = arc(normals, first, second, s, steps)
                val n1 = arc(normals, first, second, s + 1, steps)
                val a0 = inner[a] + n0 * radius
                val b0 = inner[b] + n0 * radius
                val a1 = inner[a] + n1 * radius
                val b1 = inner[b] + n1 * radius
                out.triangle(a0, b0, b1, n0, n0, n1)
                out.triangle(a0, b1, a1, n0, n1, n1)
            }
        }

        geometry.vertices.indices.forEach { vertex ->
            val faces = facesOfVertex[vertex]
            if (faces.size < 3) return@forEach
            val axis = faces.map { normals[it] }.reduce(Vec3::plus).normalized()
            val helper = if (abs(axis.x) < 0.9f) Vec3.UNIT_X else Vec3.UNIT_Y
            val tangent = helper.cross(axis).normalized()
            val bitangent = axis.cross(tangent)
            val ordered = faces.sortedBy { atan2(normals[it].dot(bitangent), normals[it].dot(tangent)) }
            val corner = inner[vertex]
            ordered.indices.forEach { i ->
                val fa = ordered[i]
                val fb = ordered[(i + 1) % ordered.size]
                val steps = arcSteps(normals[fa], normals[fb])
                for (s in 0 until steps) {
                    val p0 = arc(normals, fa, fb, s, steps)
                    val p1 = arc(normals, fa, fb, s + 1, steps)
                    val m0 = slerpUnit(axis, p0, 0.5f)
                    val m1 = slerpUnit(axis, p1, 0.5f)
                    out.triangle(corner + axis * radius, corner + m0 * radius, corner + m1 * radius, axis, m0, m1)
                    out.triangle(corner + m0 * radius, corner + p0 * radius, corner + p1 * radius, m0, p0, p1)
                    out.triangle(corner + m0 * radius, corner + p1 * radius, corner + m1 * radius, m0, p1, m1)
                }
            }
        }
    }

    private fun edgeKey(a: Int, b: Int): Long = (minOf(a, b).toLong() shl 32) or maxOf(a, b).toLong()

    private fun arcSteps(a: Vec3, b: Vec3): Int =
        ceil(acos(a.dot(b).coerceIn(-1f, 1f)) / MAX_ARC_STEP_RADIANS).toInt().coerceIn(1, 6)

    /** Canonical direction (lower face index first) so edge strips and corner fans share vertices exactly. */
    private fun arc(normals: List<Vec3>, fa: Int, fb: Int, step: Int, steps: Int): Vec3 =
        if (fa < fb) slerpUnit(normals[fa], normals[fb], step.toFloat() / steps)
        else slerpUnit(normals[fb], normals[fa], (steps - step).toFloat() / steps)

    /** Least-squares intersection of the inward-offset face planes meeting at a vertex. */
    private fun offsetVertex(planes: List<Pair<Vec3, Float>>, fallback: Vec3): Vec3 {
        val m = DoubleArray(9)
        val rhs = DoubleArray(3)
        planes.forEach { (normal, offset) ->
            val n = doubleArrayOf(normal.x.toDouble(), normal.y.toDouble(), normal.z.toDouble())
            for (row in 0..2) {
                for (column in 0..2) m[row * 3 + column] += n[row] * n[column]
                rhs[row] += n[row] * offset
            }
        }
        val det = m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6]) + m[2] * (m[3] * m[7] - m[4] * m[6])
        if (abs(det) < 1e-12) return fallback
        fun replaced(column: Int): Double {
            val c = m.copyOf()
            for (row in 0..2) c[row * 3 + column] = rhs[row]
            return c[0] * (c[4] * c[8] - c[5] * c[7]) - c[1] * (c[3] * c[8] - c[5] * c[6]) + c[2] * (c[3] * c[7] - c[4] * c[6])
        }
        return Vec3((replaced(0) / det).toFloat(), (replaced(1) / det).toFloat(), (replaced(2) / det).toFloat())
    }

    private fun appendGlyph(glyph: GlyphPlacement, out: FloatBuilder) {
        val center = glyph.center + glyph.normal * GLYPH_LIFT
        val half = glyph.size * 0.5f
        val right = glyph.right * half
        val up = glyph.up * half
        val uv = DiceGlyphAtlasLayout.uvRect(glyph.label)
        val bottomLeft = center - right - up
        val bottomRight = center + right - up
        val topRight = center + right + up
        val topLeft = center - right + up
        out.glyphVertex(bottomLeft, glyph.normal, uv[0], uv[3])
        out.glyphVertex(bottomRight, glyph.normal, uv[2], uv[3])
        out.glyphVertex(topRight, glyph.normal, uv[2], uv[1])
        out.glyphVertex(bottomLeft, glyph.normal, uv[0], uv[3])
        out.glyphVertex(topRight, glyph.normal, uv[2], uv[1])
        out.glyphVertex(topLeft, glyph.normal, uv[0], uv[1])
    }

    private class FloatBuilder {
        private var data = FloatArray(4096)
        private var size = 0

        private fun add(value: Float) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = value
        }

        private fun add(vector: Vec3) {
            add(vector.x)
            add(vector.y)
            add(vector.z)
        }

        /** Emits an outward-facing triangle, fixing the winding against the vertex normals. */
        fun triangle(p0: Vec3, p1: Vec3, p2: Vec3, n0: Vec3, n1: Vec3, n2: Vec3) {
            val geometric = (p1 - p0).cross(p2 - p0)
            if (geometric.dot(geometric) < 1e-14f) return
            if (geometric.dot(n0 + n1 + n2) < 0f) {
                add(p0); add(n0); add(p2); add(n2); add(p1); add(n1)
            } else {
                add(p0); add(n0); add(p1); add(n1); add(p2); add(n2)
            }
        }

        fun glyphVertex(position: Vec3, normal: Vec3, u: Float, v: Float) {
            add(position)
            add(normal)
            add(u)
            add(v)
        }

        fun toArray(): FloatArray = data.copyOf(size)
    }
}
