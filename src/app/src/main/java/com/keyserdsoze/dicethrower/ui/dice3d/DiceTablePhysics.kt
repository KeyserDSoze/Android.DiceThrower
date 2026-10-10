package com.keyserdsoze.dicethrower.ui.dice3d

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Lightweight 2D rigid-body simulation for the visual dice table.
 *
 * This engine never generates a roll result. It only animates already-resolved dice, keeping
 * presentation physics strictly separated from the logical RNG in DiceExpression.
 */
internal class DiceTablePhysics(
    count: Int,
    seed: Long,
    private val halfWidth: Float = DiceTableViewport.HALF_WIDTH,
    private val halfHeight: Float = DiceTableViewport.HALF_HEIGHT,
    private val laneByDieIndex: Map<Int, Int> = emptyMap(),
    /** Visual-only effect dice fly in from opposite table edges. */
    private val spawnFromEdge: Boolean = false,
) {
    private data class Body(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var angleX: Float,
        var angleY: Float,
        var angleZ: Float,
        var spinX: Float,
        var spinY: Float,
        var spinZ: Float,
        val lane: Int? = null,
    )

    data class State(
        val x: Float,
        val y: Float,
        val angleX: Float,
        val angleY: Float,
        val angleZ: Float,
    )

    val radius: Float = when {
        count <= 1 -> 0.72f
        count <= 4 -> 0.62f
        count <= 8 -> 0.52f
        else -> 0.44f
    }

    private val random = Random(seed)
    private val bodies = List(count.coerceAtMost(MAX_DICE)) { index ->
        val lane = laneByDieIndex[index]
        val columns = if (count <= 4) 2 else 3
        // A/B bodies are simulated in independent vertical table halves,
        // not moved after the physics step (which could cause overlaps).
        val groupIndex = if (lane == null) index else
            (0 until index).count { laneByDieIndex[it] == lane }
        val column = groupIndex % columns
        val row = groupIndex / columns
        val x = if (spawnFromEdge && lane == null)
            (if (index % 2 == 0) -1f else 1f) * (halfWidth - radius) * 0.88f
            else (column - (columns - 1) / 2f) * radius * 2.35f
        val y = if (lane == null) -1.65f + row * radius * 1.45f
            else (if (lane == 0) 2.15f else -2.15f) +
                (row - 1) * radius * 0.35f
        val launchAngle = random.nextFloat() * 1.7f + 0.72f
        val speed = 4.8f + random.nextFloat() * 2.6f
        Body(
            x = x,
            y = y.coerceIn(laneBounds(lane).first, laneBounds(lane).second),
            vx = if (spawnFromEdge && lane == null)
                (if (x < 0f) 1f else -1f) * speed * 0.88f
                else cos(launchAngle) * speed + (random.nextFloat() - 0.5f) * 2.4f,
            vy = (if (lane == 1) -1f else 1f) * (sin(launchAngle) * speed + 1.1f),
            angleX = random.nextFloat() * 360f,
            angleY = random.nextFloat() * 360f,
            angleZ = random.nextFloat() * 360f,
            spinX = random.signed(430f, 760f),
            spinY = random.signed(390f, 720f),
            spinZ = random.signed(220f, 480f),
            lane = lane,
        )
    }

    private var elapsed = 0f
    private var quietTime = 0f

    val settleProgress: Float
        get() {
            if (bodies.isEmpty()) return 1f
            val linear = ((elapsed - ALIGNMENT_START_SECONDS) / ALIGNMENT_DURATION_SECONDS)
                .coerceIn(0f, 1f)
            // Smoothstep avoids a visible acceleration change when the renderer begins guiding
            // the already-resolved face toward its final orientation.
            return linear * linear * (3f - 2f * linear)
        }

    val isSettled: Boolean
        get() = bodies.isEmpty() ||
            (elapsed >= MIN_ROLL_SECONDS &&
                quietTime >= REQUIRED_QUIET_SECONDS &&
                settleProgress >= 0.999f)

    fun states(): List<State> = bodies.map { body ->
        State(body.x, body.y, body.angleX, body.angleY, body.angleZ)
    }

    fun step(deltaSeconds: Float) {
        if (bodies.isEmpty() || isSettled) return
        val dt = deltaSeconds.coerceIn(0f, MAX_STEP_SECONDS)
        if (dt == 0f) return
        elapsed += dt

        val linearDamping = exp(-LINEAR_DRAG * dt)
        val angularDamping = exp(-ANGULAR_DRAG * dt)
        bodies.forEach { body ->
            body.x += body.vx * dt
            body.y += body.vy * dt
            body.angleX = (body.angleX + body.spinX * dt) % 360f
            body.angleY = (body.angleY + body.spinY * dt) % 360f
            body.angleZ = (body.angleZ + body.spinZ * dt) % 360f
            body.vx *= linearDamping
            body.vy *= linearDamping
            body.spinX *= angularDamping
            body.spinY *= angularDamping
            body.spinZ *= angularDamping
            collideWithWalls(body)
        }

        repeat(2) { resolvePairCollisions() }

        val quiet = bodies.all { body ->
            body.vx * body.vx + body.vy * body.vy < LINEAR_SLEEP_SPEED * LINEAR_SLEEP_SPEED &&
                abs(body.spinX) + abs(body.spinY) + abs(body.spinZ) < ANGULAR_SLEEP_SUM
        }
        quietTime = if (quiet) quietTime + dt else 0f

        if (elapsed >= MAX_ROLL_SECONDS) {
            bodies.forEach { body ->
                body.vx = 0f
                body.vy = 0f
                body.spinX = 0f
                body.spinY = 0f
                body.spinZ = 0f
            }
            quietTime = REQUIRED_QUIET_SECONDS
        }
    }

    private fun laneBounds(lane: Int?): Pair<Float, Float> = when (lane) {
        0 -> 0.22f + radius to halfHeight - radius
        1 -> -halfHeight + radius to -0.22f - radius
        else -> -halfHeight + radius to halfHeight - radius
    }

    private fun collideWithWalls(body: Body) {
        val maxX = halfWidth - radius
        val (minY, maxY) = laneBounds(body.lane)
        if (body.x < -maxX || body.x > maxX) {
            body.x = body.x.coerceIn(-maxX, maxX)
            body.vx = -body.vx * WALL_RESTITUTION
            body.spinY *= -0.82f
        }
        if (body.y < minY || body.y > maxY) {
            body.y = body.y.coerceIn(minY, maxY)
            body.vy = -body.vy * WALL_RESTITUTION
            body.spinX *= -0.82f
        }
    }

    private fun resolvePairCollisions() {
        val minimumDistance = radius * 2f
        for (firstIndex in 0 until bodies.lastIndex) {
            for (secondIndex in firstIndex + 1 until bodies.size) {
                val first = bodies[firstIndex]
                val second = bodies[secondIndex]
                if (first.lane != second.lane) continue
                var dx = second.x - first.x
                var dy = second.y - first.y
                var distanceSquared = dx * dx + dy * dy
                if (distanceSquared >= minimumDistance * minimumDistance) continue

                if (distanceSquared < 0.00001f) {
                    dx = 0.001f * (secondIndex + 1)
                    dy = 0.001f * (firstIndex + 1)
                    distanceSquared = dx * dx + dy * dy
                }
                val distance = sqrt(distanceSquared)
                val nx = dx / distance
                val ny = dy / distance
                val overlap = minimumDistance - distance
                first.x -= nx * overlap * 0.5f
                first.y -= ny * overlap * 0.5f
                second.x += nx * overlap * 0.5f
                second.y += ny * overlap * 0.5f

                val relativeNormalVelocity = (second.vx - first.vx) * nx + (second.vy - first.vy) * ny
                if (relativeNormalVelocity < 0f) {
                    val impulse = -(1f + DIE_RESTITUTION) * relativeNormalVelocity * 0.5f
                    first.vx -= impulse * nx
                    first.vy -= impulse * ny
                    second.vx += impulse * nx
                    second.vy += impulse * ny
                    val spinKick = impulse * 31f
                    first.spinZ -= spinKick
                    second.spinZ += spinKick
                }
            }
        }
    }

    private fun Random.signed(min: Float, max: Float): Float {
        val value = min + nextFloat() * (max - min)
        return if (nextBoolean()) value else -value
    }

    private companion object {
        const val MAX_DICE = 12
        const val MAX_STEP_SECONDS = 1f / 30f
        const val MIN_ROLL_SECONDS = 1.25f
        const val MAX_ROLL_SECONDS = 2.8f
        const val ALIGNMENT_START_SECONDS = 1.15f
        const val ALIGNMENT_DURATION_SECONDS = 0.85f
        const val REQUIRED_QUIET_SECONDS = 0.20f
        const val LINEAR_DRAG = 1.72f
        const val ANGULAR_DRAG = 2.35f
        const val WALL_RESTITUTION = 0.67f
        const val DIE_RESTITUTION = 0.72f
        const val LINEAR_SLEEP_SPEED = 0.16f
        const val ANGULAR_SLEEP_SUM = 24f
    }
}
