package com.keyserdsoze.dicethrower.ui.dice3d

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

internal data class Vec3(
    val x: Float,
    val y: Float,
    val z: Float,
) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(value: Float) = Vec3(x * value, y * value, z * value)
    operator fun unaryMinus() = Vec3(-x, -y, -z)

    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vec3): Vec3 = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x,
    )

    fun length(): Float = sqrt(dot(this))

    fun normalized(): Vec3 {
        val length = sqrt(max(0.000001f, dot(this)))
        return Vec3(x / length, y / length, z / length)
    }

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val UNIT_X = Vec3(1f, 0f, 0f)
        val UNIT_Y = Vec3(0f, 1f, 0f)
        val UNIT_Z = Vec3(0f, 0f, 1f)
    }
}

/** Unit quaternion (w, x, y, z) describing a body-to-world rotation. */
internal data class Quat(
    val w: Float,
    val x: Float,
    val y: Float,
    val z: Float,
) {
    operator fun times(other: Quat) = Quat(
        w * other.w - x * other.x - y * other.y - z * other.z,
        w * other.x + x * other.w + y * other.z - z * other.y,
        w * other.y - x * other.z + y * other.w + z * other.x,
        w * other.z + x * other.y - y * other.x + z * other.w,
    )

    fun conjugate() = Quat(w, -x, -y, -z)

    fun dot(other: Quat): Float = w * other.w + x * other.x + y * other.y + z * other.z

    fun normalized(): Quat {
        val length = sqrt(max(1e-12f, w * w + x * x + y * y + z * z))
        return Quat(w / length, x / length, y / length, z / length)
    }

    fun rotate(vector: Vec3): Vec3 {
        val tx = 2f * (y * vector.z - z * vector.y)
        val ty = 2f * (z * vector.x - x * vector.z)
        val tz = 2f * (x * vector.y - y * vector.x)
        return Vec3(
            vector.x + w * tx + (y * tz - z * ty),
            vector.y + w * ty + (z * tx - x * tz),
            vector.z + w * tz + (x * ty - y * tx),
        )
    }

    /** Writes this rotation as a column-major 4x4 OpenGL matrix. */
    fun toMatrix(out: FloatArray, offset: Int = 0) {
        val xx = x * x
        val yy = y * y
        val zz = z * z
        val xy = x * y
        val xz = x * z
        val yz = y * z
        val wx = w * x
        val wy = w * y
        val wz = w * z
        out[offset] = 1f - 2f * (yy + zz)
        out[offset + 1] = 2f * (xy + wz)
        out[offset + 2] = 2f * (xz - wy)
        out[offset + 3] = 0f
        out[offset + 4] = 2f * (xy - wz)
        out[offset + 5] = 1f - 2f * (xx + zz)
        out[offset + 6] = 2f * (yz + wx)
        out[offset + 7] = 0f
        out[offset + 8] = 2f * (xz + wy)
        out[offset + 9] = 2f * (yz - wx)
        out[offset + 10] = 1f - 2f * (xx + yy)
        out[offset + 11] = 0f
        out[offset + 12] = 0f
        out[offset + 13] = 0f
        out[offset + 14] = 0f
        out[offset + 15] = 1f
    }

    companion object {
        val IDENTITY = Quat(1f, 0f, 0f, 0f)

        fun fromAxisAngle(axis: Vec3, radians: Float): Quat {
            val unit = axis.normalized()
            val half = radians * 0.5f
            val s = sin(half)
            return Quat(cos(half), unit.x * s, unit.y * s, unit.z * s)
        }

        /** Shortest rotation taking the unit vector [from] onto the unit vector [to]. */
        fun between(from: Vec3, to: Vec3): Quat {
            val a = from.normalized()
            val b = to.normalized()
            val d = a.dot(b)
            if (d > 0.999999f) return IDENTITY
            if (d < -0.999999f) {
                val helper = if (abs(a.x) < 0.9f) Vec3.UNIT_X else Vec3.UNIT_Y
                return fromAxisAngle(helper.cross(a), Math.PI.toFloat())
            }
            val axis = a.cross(b)
            return Quat(1f + d, axis.x, axis.y, axis.z).normalized()
        }

        /** Rotation whose matrix columns are the given orthonormal right-handed axes. */
        fun fromBasis(xAxis: Vec3, yAxis: Vec3, zAxis: Vec3): Quat {
            val m00 = xAxis.x
            val m10 = xAxis.y
            val m20 = xAxis.z
            val m01 = yAxis.x
            val m11 = yAxis.y
            val m21 = yAxis.z
            val m02 = zAxis.x
            val m12 = zAxis.y
            val m22 = zAxis.z
            val trace = m00 + m11 + m22
            return when {
                trace > 0f -> {
                    val s = sqrt(trace + 1f) * 2f
                    Quat(0.25f * s, (m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s)
                }
                m00 > m11 && m00 > m22 -> {
                    val s = sqrt(1f + m00 - m11 - m22) * 2f
                    Quat((m21 - m12) / s, 0.25f * s, (m01 + m10) / s, (m02 + m20) / s)
                }
                m11 > m22 -> {
                    val s = sqrt(1f + m11 - m00 - m22) * 2f
                    Quat((m02 - m20) / s, (m01 + m10) / s, 0.25f * s, (m12 + m21) / s)
                }
                else -> {
                    val s = sqrt(1f + m22 - m00 - m11) * 2f
                    Quat((m10 - m01) / s, (m02 + m20) / s, (m12 + m21) / s, 0.25f * s)
                }
            }.normalized()
        }
    }
}

/** Normalized linear interpolation along the shortest arc; smooth enough for 120 Hz samples. */
internal fun nlerp(from: Quat, to: Quat, t: Float): Quat {
    val sign = if (from.dot(to) < 0f) -1f else 1f
    val inverse = 1f - t
    return Quat(
        from.w * inverse + to.w * t * sign,
        from.x * inverse + to.x * t * sign,
        from.y * inverse + to.y * t * sign,
        from.z * inverse + to.z * t * sign,
    ).normalized()
}

/** Spherical interpolation between two unit vectors (used for rounded bevel normals). */
internal fun slerpUnit(from: Vec3, to: Vec3, t: Float): Vec3 {
    val d = from.dot(to).coerceIn(-1f, 1f)
    val angle = kotlin.math.acos(d)
    if (angle < 1e-4f) return (from * (1f - t) + to * t).normalized()
    val s = sin(angle)
    return (from * (sin((1f - t) * angle) / s) + to * (sin(t * angle) / s)).normalized()
}

internal data class DiePose(
    val position: Vec3,
    val rotation: Quat,
)
