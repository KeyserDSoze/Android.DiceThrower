package com.keyserdsoze.dicethrower.ui.dice3d

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Pure predicate shared by collision simulation and JVM regression tests. */
internal fun shouldResolveDiceCollision(firstLane: Int?, secondLane: Int?): Boolean =
    firstLane == null || secondLane == null || firstLane == secondLane

/**
 * The compared candidates share a compact, central playing area. Keep a clear gap between
 * lanes and leave the table edges free for headers and results. These coordinates only affect
 * rendering, never the logical dice outcome.
 */
internal object CandidateLaneLayout {
    const val CENTER_GAP = 0.22f
    const val MAX_DISTANCE_FROM_CENTER = 2.85f

    /** Invisible wall planes (min Y, max Y) that a lane's dice can never cross. */
    fun band(lane: Int?, halfHeight: Float): Pair<Float, Float> = when (lane) {
        null -> -halfHeight to halfHeight
        0 -> CENTER_GAP to minOf(halfHeight, MAX_DISTANCE_FROM_CENTER)
        else -> -minOf(halfHeight, MAX_DISTANCE_FROM_CENTER) to -CENTER_GAP
    }

    fun staticCenter(lane: Int): Float = if (lane == 0) 1.45f else -1.45f
}

internal data class DieThrowSpec(
    val sides: Int,
    /** Logical value already chosen by the dice engine; the simulation only decides the motion. */
    val value: Int,
    val lane: Int? = null,
    /** Die settled in a previous effect stage: a static obstacle that never moves again. */
    val fixedPose: DiePose? = null,
)

/**
 * A pre-simulated throw sampled at [sampleRate]. Every pose already includes the body-space
 * symmetry remap, so each die physically lands showing its pre-decided value.
 */
internal class BakedDiceThrow internal constructor(
    val dieCount: Int,
    val sampleRate: Float,
    val sampleCount: Int,
    private val samples: FloatArray,
    private val remaps: Array<Quat>,
    private val fixedPoses: Array<DiePose?>,
) {
    val durationSeconds: Float = if (sampleCount <= 1) 0f else (sampleCount - 1) / sampleRate

    fun pose(die: Int, timeSeconds: Float): DiePose {
        fixedPoses[die]?.let { return it }
        val cursor = (timeSeconds.coerceAtLeast(0f) * sampleRate)
        val index = cursor.toInt().coerceIn(0, sampleCount - 1)
        val next = (index + 1).coerceAtMost(sampleCount - 1)
        val fraction = (cursor - index).coerceIn(0f, 1f)
        val a = offset(index, die)
        val b = offset(next, die)
        val position = Vec3(
            samples[a] + (samples[b] - samples[a]) * fraction,
            samples[a + 1] + (samples[b + 1] - samples[a + 1]) * fraction,
            samples[a + 2] + (samples[b + 2] - samples[a + 2]) * fraction,
        )
        val rotation = nlerp(
            Quat(samples[a + 3], samples[a + 4], samples[a + 5], samples[a + 6]),
            Quat(samples[b + 3], samples[b + 4], samples[b + 5], samples[b + 6]),
            fraction,
        )
        return DiePose(position, (rotation * remaps[die]).normalized())
    }

    fun finalPose(die: Int): DiePose = pose(die, durationSeconds)

    private fun offset(sample: Int, die: Int) = (sample * dieCount + die) * STRIDE

    internal companion object {
        const val STRIDE = 7
    }
}

/**
 * Deterministic 3D rigid-body dice simulation: gravity, bounces, Coulomb friction, rolling and
 * polyhedron contacts against the felt, the rails and the other dice.
 *
 * The throw is simulated to rest before the first frame. The die then receives a body-space
 * rotation from its own symmetry group that maps the engine-selected value onto the face that
 * physically ended on top. The shape is invariant under that rotation, so every simulated
 * contact stays valid: the motion is entirely physical and the visible value is never altered.
 * Physics uses its own seeded RNG and never touches the logical dice RNG.
 */
internal object DiceThrowSimulator {
    const val SAMPLE_RATE = 120f
    private const val STEPS_PER_SAMPLE = 2
    private const val DT = 1.0 / (SAMPLE_RATE * STEPS_PER_SAMPLE)
    private const val GRAVITY = 64.0
    private const val ITERATIONS = 7
    private const val MAX_CONTACTS = 1536

    private const val FLOOR_FRICTION = 0.46
    private const val FLOOR_RESTITUTION = 0.38
    private const val WALL_FRICTION = 0.20
    private const val WALL_RESTITUTION = 0.52
    private const val LANE_RESTITUTION = 0.18
    private const val DIE_FRICTION = 0.18
    private const val DIE_RESTITUTION = 0.42
    private const val RESTITUTION_THRESHOLD = 0.9
    private const val CONTACT_MARGIN = 0.004

    private const val LINEAR_DAMPING = 0.06
    private const val ANGULAR_DAMPING = 0.20
    private const val ROLLING_DAMPING = 0.75

    private const val SLEEP_LINEAR = 0.09
    private const val SLEEP_ANGULAR = 0.32
    private const val SLEEP_SECONDS = 0.16
    private const val WAKE_SPEED = 0.7
    private const val STACK_SLIDE = 16.0

    private const val MIN_SECONDS = 0.8
    private const val ASSIST_START_SECONDS = 2.1
    private const val ASSIST_RATE = 9.0
    private const val MAX_SECONDS = 3.4
    private const val FLAT_TOLERANCE_COS = 0.99985f
    private const val SETTLE_TAIL_SECONDS = 0.10f
    private const val TOPPLE_TAIL_SECONDS = 0.32f

    /**
     * Resting pose with the chosen value on top, for still tables and previews. The numeral is
     * turned upright on screen; [yawRadians] only adds a small natural variation.
     */
    fun restingPose(sides: Int, value: Int, x: Float, y: Float, yawRadians: Float, scale: Float): DiePose {
        val geometry = DieGeometries.forSides(sides)
        val anchor = geometry.anchors[geometry.anchorIndicesForValue(value).first()]
        val aligned = Quat.between(anchor, Vec3.UNIT_Z)
        val glyph = geometry.glyphs.firstOrNull { it.normal.dot(anchor) > 0.999f }
        val yaw = if (glyph == null) yawRadians else {
            val up = aligned.rotate(glyph.up)
            kotlin.math.atan2(up.x, up.y) + sin(yawRadians) * 0.20f
        }
        val rotation = (Quat.fromAxisAngle(Vec3.UNIT_Z, yaw) * aligned).normalized()
        val lowest = geometry.vertices.minOf { rotation.rotate(it).z }
        return DiePose(Vec3(x, y, -lowest * scale), rotation)
    }

    private val warmedUp = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Runs a throw-away simulation on a background thread so the JIT has compiled the solver
     * before the player's first throw (a cold first bake can take a large fraction of a second).
     */
    fun warmUpInBackground() {
        if (!warmedUp.compareAndSet(false, true)) return
        Thread({
            runCatching {
                val specs = listOf(20, 6, 8, 12, 10, 4).map { DieThrowSpec(it, 1) }
                repeat(3) { bake(specs, 7L + it, DiceTableViewport.dieScaleFor(specs.size)) }
                DiceMeshFactory.create(20)
            }
        }, "dice-physics-warmup").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }.start()
    }

    fun bake(
        specs: List<DieThrowSpec>,
        seed: Long,
        dieScale: Float,
        halfWidth: Float = DiceTableViewport.HALF_WIDTH,
        halfHeight: Float = DiceTableViewport.HALF_HEIGHT,
        spawnFromEdge: Boolean = false,
    ): BakedDiceThrow {
        val random = Random(seed)
        val bodies = specs.map { spec ->
            Body(DieGeometries.forSides(spec.sides), dieScale.toDouble(), spec.lane, spec.fixedPose != null).also { body ->
                val (minY, maxY) = CandidateLaneLayout.band(spec.lane, halfHeight)
                body.minX = -halfWidth.toDouble()
                body.maxX = halfWidth.toDouble()
                body.minY = minY.toDouble()
                body.maxY = maxY.toDouble()
                spec.fixedPose?.let(body::place)
            }
        }
        spawn(bodies.filterNot { it.isStatic }, random, halfWidth, halfHeight, spawnFromEdge)
        bodies.forEach(Body::refreshWorld)

        val world = World(bodies)
        val dynamic = bodies.filterNot { it.isStatic }
        val frames = ArrayList<FloatArray>(512)
        frames += snapshot(bodies)
        var time = 0.0
        var steps = 0
        while (true) {
            world.step(time)
            time += DT
            steps++
            if (steps % STEPS_PER_SAMPLE == 0) frames += snapshot(bodies)
            if (time >= MIN_SECONDS && dynamic.all { it.asleep }) break
            if (time >= MAX_SECONDS) break
        }

        // Put every die exactly flat on a face. A die stopped cocked against a rail or another die
        // topples onto its nearest face; for the others this correction is imperceptible.
        val settled = arrayOfNulls<DiePose>(bodies.size)
        bodies.forEachIndexed { index, body -> if (!body.isStatic) settled[index] = body.flatPose() }
        relax(settled, bodies, specs)
        val toppling = bodies.indices.any { index ->
            val flat = settled[index] ?: return@any false
            val body = bodies[index]
            abs(body.rotation().dot(flat.rotation)) < 0.9995f || (body.position() - flat.position).length() > 0.05f
        }
        val tailSamples = ((if (toppling) TOPPLE_TAIL_SECONDS else SETTLE_TAIL_SECONDS) * SAMPLE_RATE).toInt().coerceAtLeast(1)
        val start = bodies.map { DiePose(it.position(), it.rotation()) }
        repeat(tailSamples) { sample ->
            val t = (sample + 1f) / tailSamples
            val eased = t * t * (3f - 2f * t)
            frames += FloatArray(bodies.size * BakedDiceThrow.STRIDE).also { frame ->
                bodies.indices.forEach { index ->
                    val from = start[index]
                    val to = settled[index] ?: from
                    val rotation = nlerp(from.rotation, to.rotation, eased)
                    var position = from.position + (to.position - from.position) * eased
                    if (!bodies[index].isStatic) {
                        // Pivot on the felt while toppling: never let a corner dip below the table.
                        val lowest = bodies[index].lowestOffset(rotation)
                        if (position.z + lowest < 0f) position = position.copy(z = -lowest)
                    }
                    write(frame, index, position, rotation)
                }
            }
        }

        val remaps = Array(bodies.size) { index ->
            val body = bodies[index]
            if (body.isStatic) Quat.IDENTITY else body.geometry.remapFor(specs[index].value, settled[index]!!.rotation)
        }
        val samples = FloatArray(frames.size * bodies.size * BakedDiceThrow.STRIDE)
        frames.forEachIndexed { index, frame -> frame.copyInto(samples, index * frame.size) }
        return BakedDiceThrow(
            dieCount = bodies.size,
            sampleRate = SAMPLE_RATE,
            sampleCount = frames.size,
            samples = samples,
            remaps = remaps,
            fixedPoses = Array(bodies.size) { specs[it].fixedPose },
        )
    }

    private fun snapshot(bodies: List<Body>): FloatArray = FloatArray(bodies.size * BakedDiceThrow.STRIDE).also { frame ->
        bodies.forEachIndexed { index, body -> write(frame, index, body.position(), body.rotation()) }
    }

    /**
     * Final safety net on the resting layout: separates dice that ended up touching too closely
     * (or one balanced on another) and keeps every die inside its rails and candidate lane.
     */
    private fun relax(poses: Array<DiePose?>, bodies: List<Body>, specs: List<DieThrowSpec>) {
        fun poseOf(index: Int): DiePose = poses[index] ?: specs[index].fixedPose ?: DiePose(bodies[index].position(), bodies[index].rotation())
        repeat(48) { iteration ->
            var moved = false
            for (i in bodies.indices) for (j in i + 1 until bodies.size) {
                if (poses[i] == null && poses[j] == null) continue
                if (!shouldResolveDiceCollision(bodies[i].lane, bodies[j].lane)) continue
                val a = poseOf(i)
                val b = poseOf(j)
                val reach = (bodies[i].boundRadius + bodies[j].boundRadius).toFloat()
                val delta = b.position - a.position
                if (delta.length() >= reach) continue
                val inner = (bodies[i].restHeight + bodies[j].restHeight).toFloat() - delta.length()
                val depth = maxOf(
                    inner,
                    penetration(bodies[i], a, bodies[j], b),
                    penetration(bodies[j], b, bodies[i], a),
                )
                if (depth <= 1e-4f) continue
                var dx = delta.x
                var dy = delta.y
                val planar = sqrt(dx * dx + dy * dy)
                if (planar < 1e-4f) {
                    val angle = (i * 2.399 + j * 0.7 + iteration).toFloat()
                    dx = cos(angle)
                    dy = sin(angle)
                } else {
                    dx /= planar
                    dy /= planar
                }
                val push = depth + 0.01f
                val shareA = if (poses[i] == null) 0f else if (poses[j] == null) 1f else 0.5f
                val shareB = 1f - shareA
                if (shareA > 0f) poses[i] = a.copy(position = a.position - Vec3(dx, dy, 0f) * (push * shareA))
                if (shareB > 0f) poses[j] = b.copy(position = b.position + Vec3(dx, dy, 0f) * (push * shareB))
                moved = true
            }
            bodies.indices.forEach { index ->
                val pose = poses[index] ?: return@forEach
                val clamped = bodies[index].clampToRails(pose)
                if (clamped != pose) {
                    poses[index] = clamped
                    moved = true
                }
            }
            if (!moved) return
        }
    }

    /** Depth of the deepest vertex of [owner] inside the convex hull of [hull]. */
    private fun penetration(owner: Body, ownerPose: DiePose, hull: Body, hullPose: DiePose): Float {
        val geometry = hull.geometry
        val normals = geometry.faceNormals.map(hullPose.rotation::rotate)
        val scale = (hull.boundRadius / geometry.circumradius).toFloat()
        val ownerScale = (owner.boundRadius / owner.geometry.circumradius).toFloat()
        var deepest = 0f
        owner.geometry.vertices.forEach { vertex ->
            val point = ownerPose.position + ownerPose.rotation.rotate(vertex) * ownerScale - hullPose.position
            var best = -Float.MAX_VALUE
            for (face in normals.indices) {
                val distance = normals[face].dot(point) - geometry.faceOffsets[face] * scale
                if (distance >= 0f) return@forEach
                best = maxOf(best, distance)
            }
            deepest = maxOf(deepest, -best)
        }
        return deepest
    }

    private fun write(frame: FloatArray, index: Int, position: Vec3, rotation: Quat) {
        val o = index * BakedDiceThrow.STRIDE
        frame[o] = position.x
        frame[o + 1] = position.y
        frame[o + 2] = position.z
        frame[o + 3] = rotation.w
        frame[o + 4] = rotation.x
        frame[o + 5] = rotation.y
        frame[o + 6] = rotation.z
    }

    /**
     * A natural throw: the dice leave the hand together near the bottom rail, fly up the table
     * rolling forward, bounce on the felt and spread out. Candidate lanes are thrown across their
     * own band; effect dice fly in from the side rails.
     */
    private fun spawn(dynamic: List<Body>, random: Random, halfWidth: Float, halfHeight: Float, spawnFromEdge: Boolean) {
        val groups = dynamic.groupBy { it.lane }
        groups.forEach { (lane, members) ->
            when {
                lane != null -> spawnLane(members, lane, random, halfWidth, halfHeight)
                spawnFromEdge -> spawnFromRails(members, random, halfWidth, halfHeight)
                else -> spawnHandful(members, random, halfWidth, halfHeight)
            }
        }
    }

    private fun spawnHandful(members: List<Body>, random: Random, halfWidth: Float, halfHeight: Float) {
        val bound = members.maxOf { it.boundRadius }
        val spacing = bound * 2.15
        val usableWidth = 2.0 * (halfWidth - bound)
        val columns = min(members.size, (usableWidth / spacing).toInt() + 1).coerceIn(1, 4)
        val rows = (members.size + columns - 1) / columns
        // Rows go back towards the bottom rail but never above each other: stacked spawns
        // would land dice on top of dice.
        val rowSpacing = bound * 2.08
        // Small handfuls leave the player's hand right behind the footer; bigger ones start a bit
        // higher so every row still fits inside the bottom rail.
        val launchY = max(-halfHeight * 0.80, -halfHeight + bound * 1.1 + (rows - 1) * rowSpacing)
        val heading = random.range(-0.28, 0.28)
        val launchX = random.range(-0.30, 0.30) * halfWidth
        val speed = random.range(9.5, 11.6)
        members.forEachIndexed { index, body ->
            val column = index % columns
            val row = index / columns
            val rowShift = if (row % 2 == 1) spacing * 0.25 else 0.0
            val x = (launchX + (column - (columns - 1) / 2.0) * spacing + rowShift + random.range(-0.08, 0.08))
                .coerceIn(-halfWidth + bound, halfWidth - bound)
            val y = (launchY - row * rowSpacing + random.range(-0.06, 0.06)).coerceAtLeast(-halfHeight + bound * 1.05)
            val z = 1.65 + row * 0.24 + random.range(0.0, 0.55)
            // Dice leave the hand together but never in lockstep: each gets its own speed and line.
            val dieSpeed = speed * random.range(0.84, 1.10)
            val dirX = sin(heading + random.range(-0.28, 0.28)) - x / halfWidth * 0.14
            val dirY = cos(heading)
            body.launch(x, y, z, dirX * dieSpeed, dirY * dieSpeed, random.range(0.8, 2.6), random)
        }
    }

    private fun spawnLane(members: List<Body>, lane: Int, random: Random, halfWidth: Float, halfHeight: Float) {
        val (minY, maxY) = CandidateLaneLayout.band(lane, halfHeight)
        val bound = members.maxOf { it.boundRadius }
        val spacing = bound * 2.1
        val rows = (((maxY - minY) - 2.0 * bound) / spacing).toInt().plus(1).coerceIn(1, 3)
        val columns = ((2.0 * (halfWidth - bound)) / spacing).toInt().plus(1).coerceAtLeast(1)
        val direction = if (lane == 0) 1.0 else -1.0
        val startX = -direction * (halfWidth - bound * 1.05)
        val centerY = (minY + maxY) / 2.0
        members.forEachIndexed { index, body ->
            val row = index % rows
            val column = (index / rows) % columns
            // Only a truly overfull lane uses a second wave, offset into the gaps of the first.
            val wave = index / (rows * columns)
            val x = (startX + direction * (column + wave * 0.5) * spacing).coerceIn(-halfWidth + bound, halfWidth - bound)
            val y = (centerY + (row - (rows - 1) / 2.0 + wave * 0.5) * spacing + random.range(-0.04, 0.04))
                .coerceIn(minY + bound, maxY - bound)
            val z = 1.35 + wave * spacing * 1.4 + random.range(0.0, 0.3)
            val speed = random.range(6.8, 8.6) * (1.0 - column * 0.06)
            body.launch(x, y, z, direction * speed, random.range(-0.4, 0.4), random.range(0.5, 1.4), random)
        }
    }

    private fun spawnFromRails(members: List<Body>, random: Random, halfWidth: Float, halfHeight: Float) {
        members.forEachIndexed { index, body ->
            val side = if (index % 2 == 0) -1.0 else 1.0
            val slot = (index / 2) % 4
            val x = side * (halfWidth - body.boundRadius * 1.1)
            val y = ((slot - 1.5) * body.boundRadius * 2.3 + random.range(-0.5, 0.5))
                .coerceIn(-halfHeight + body.boundRadius, halfHeight - body.boundRadius.toDouble())
            val z = 1.6 + (index / 8) * body.boundRadius * 2.2 + random.range(0.0, 0.4)
            body.launch(x, y, z, -side * random.range(6.4, 8.2), random.range(-1.0, 1.0), random.range(0.8, 1.8), random)
        }
    }

    private fun Random.range(from: Double, until: Double) = from + nextDouble() * (until - from)

    private class Body(
        val geometry: DieGeometry,
        scale: Double,
        val lane: Int?,
        val isStatic: Boolean,
    ) {
        val vertexCount = geometry.vertices.size
        val local = DoubleArray(vertexCount * 3).also { array ->
            geometry.vertices.forEachIndexed { index, vertex ->
                array[index * 3] = vertex.x * scale
                array[index * 3 + 1] = vertex.y * scale
                array[index * 3 + 2] = vertex.z * scale
            }
        }
        val world = DoubleArray(vertexCount * 3)
        val faceCount = geometry.faces.size
        val faceLocal = DoubleArray(faceCount * 3).also { array ->
            geometry.faceNormals.forEachIndexed { index, normal ->
                array[index * 3] = normal.x.toDouble()
                array[index * 3 + 1] = normal.y.toDouble()
                array[index * 3 + 2] = normal.z.toDouble()
            }
        }
        val faceOffset = DoubleArray(faceCount) { geometry.faceOffsets[it] * scale }
        val faceWorld = DoubleArray(faceCount * 3)
        val boundRadius = geometry.circumradius * scale
        val restHeight = geometry.inradius * scale
        private val scaleF = scale.toFloat()
        val invMass = if (isStatic) 0.0 else 1.0
        private val inertia = geometry.inertiaDiagonal
        val invIx = if (isStatic) 0.0 else 1.0 / (inertia.x * scale * scale)
        val invIy = if (isStatic) 0.0 else 1.0 / (inertia.y * scale * scale)
        val invIz = if (isStatic) 0.0 else 1.0 / (inertia.z * scale * scale)

        var px = 0.0; var py = 0.0; var pz = 0.0
        var vx = 0.0; var vy = 0.0; var vz = 0.0
        var wx = 0.0; var wy = 0.0; var wz = 0.0
        var qw = 1.0; var qx = 0.0; var qy = 0.0; var qz = 0.0
        val r = DoubleArray(9)
        var minX = 0.0; var maxX = 0.0; var minY = 0.0; var maxY = 0.0
        var asleep = isStatic
        var quiet = 0.0
        var floorContacts = 0

        /** Effective inverse mass: sleeping dice behave as immovable until something wakes them. */
        val activeInvMass get() = if (asleep) 0.0 else invMass

        fun place(pose: DiePose) {
            px = pose.position.x.toDouble(); py = pose.position.y.toDouble(); pz = pose.position.z.toDouble()
            qw = pose.rotation.w.toDouble(); qx = pose.rotation.x.toDouble()
            qy = pose.rotation.y.toDouble(); qz = pose.rotation.z.toDouble()
        }

        fun launch(x: Double, y: Double, z: Double, velocityX: Double, velocityY: Double, velocityZ: Double, random: Random) {
            px = x; py = y; pz = z
            vx = velocityX; vy = velocityY; vz = velocityZ
            // Uniformly random initial orientation (Shoemake).
            val u1 = random.nextDouble(); val u2 = random.nextDouble(); val u3 = random.nextDouble()
            val a = sqrt(1.0 - u1); val b = sqrt(u1)
            qx = a * sin(2 * PI * u2); qy = a * cos(2 * PI * u2)
            qz = b * sin(2 * PI * u3); qw = b * cos(2 * PI * u3)
            // A thrown die rolls forward around the axis perpendicular to its flight, plus wobble.
            val speed = sqrt(velocityX * velocityX + velocityY * velocityY).coerceAtLeast(1e-6)
            val rollX = -velocityY / speed
            val rollY = velocityX / speed
            val spin = 13.0 + random.nextDouble() * 12.0
            var ax = rollX * 0.8 + (random.nextDouble() - 0.5) * 1.1
            var ay = rollY * 0.8 + (random.nextDouble() - 0.5) * 1.1
            var az = (random.nextDouble() - 0.5) * 1.3
            val length = sqrt(ax * ax + ay * ay + az * az).coerceAtLeast(1e-6)
            ax /= length; ay /= length; az /= length
            wx = ax * spin; wy = ay * spin; wz = az * spin
            asleep = false
        }

        fun position() = Vec3(px.toFloat(), py.toFloat(), pz.toFloat())
        fun rotation() = Quat(qw.toFloat(), qx.toFloat(), qy.toFloat(), qz.toFloat()).normalized()

        fun refreshWorld() {
            val xx = qx * qx; val yy = qy * qy; val zz = qz * qz
            val xy = qx * qy; val xz = qx * qz; val yz = qy * qz
            val wxq = qw * qx; val wyq = qw * qy; val wzq = qw * qz
            r[0] = 1 - 2 * (yy + zz); r[1] = 2 * (xy - wzq); r[2] = 2 * (xz + wyq)
            r[3] = 2 * (xy + wzq); r[4] = 1 - 2 * (xx + zz); r[5] = 2 * (yz - wxq)
            r[6] = 2 * (xz - wyq); r[7] = 2 * (yz + wxq); r[8] = 1 - 2 * (xx + yy)
            for (i in 0 until vertexCount) {
                val lx = local[i * 3]; val ly = local[i * 3 + 1]; val lz = local[i * 3 + 2]
                world[i * 3] = px + r[0] * lx + r[1] * ly + r[2] * lz
                world[i * 3 + 1] = py + r[3] * lx + r[4] * ly + r[5] * lz
                world[i * 3 + 2] = pz + r[6] * lx + r[7] * ly + r[8] * lz
            }
            for (i in 0 until faceCount) {
                val lx = faceLocal[i * 3]; val ly = faceLocal[i * 3 + 1]; val lz = faceLocal[i * 3 + 2]
                faceWorld[i * 3] = r[0] * lx + r[1] * ly + r[2] * lz
                faceWorld[i * 3 + 1] = r[3] * lx + r[4] * ly + r[5] * lz
                faceWorld[i * 3 + 2] = r[6] * lx + r[7] * ly + r[8] * lz
            }
        }

        fun translate(dx: Double, dy: Double, dz: Double) {
            px += dx; py += dy; pz += dz
            for (i in 0 until vertexCount) {
                world[i * 3] += dx; world[i * 3 + 1] += dy; world[i * 3 + 2] += dz
            }
        }

        /** out = R · diag(invI) · Rᵀ · v, or zero while asleep/static. */
        fun invInertiaWorld(x: Double, y: Double, z: Double, out: DoubleArray) {
            if (asleep || isStatic) {
                out[0] = 0.0; out[1] = 0.0; out[2] = 0.0
                return
            }
            val l0 = (r[0] * x + r[3] * y + r[6] * z) * invIx
            val l1 = (r[1] * x + r[4] * y + r[7] * z) * invIy
            val l2 = (r[2] * x + r[5] * y + r[8] * z) * invIz
            out[0] = r[0] * l0 + r[1] * l1 + r[2] * l2
            out[1] = r[3] * l0 + r[4] * l1 + r[5] * l2
            out[2] = r[6] * l0 + r[7] * l1 + r[8] * l2
        }

        fun integrate(dt: Double) {
            px += vx * dt; py += vy * dt; pz += vz * dt
            val dw = -(wx * qx + wy * qy + wz * qz)
            val dx = qw * wx + (wy * qz - wz * qy)
            val dy = qw * wy + (wz * qx - wx * qz)
            val dz = qw * wz + (wx * qy - wy * qx)
            qw += 0.5 * dt * dw; qx += 0.5 * dt * dx; qy += 0.5 * dt * dy; qz += 0.5 * dt * dz
            val length = sqrt(qw * qw + qx * qx + qy * qy + qz * qz)
            qw /= length; qx /= length; qy /= length; qz /= length
        }

        fun wake() {
            if (!isStatic) {
                asleep = false
                quiet = 0.0
            }
        }

        /** Same orientation snapped exactly flat on the face pointing down, resting on the felt. */
        fun flatPose(): DiePose {
            val rotation = rotation()
            val down = geometry.faceNormals.maxBy { -rotation.rotate(it).z }
            val correction = Quat.between(rotation.rotate(down), -Vec3.UNIT_Z)
            val flat = (correction * rotation).normalized()
            val lowest = geometry.vertices.minOf { flat.rotate(it).z } * scaleF
            return clampToRails(DiePose(Vec3(px.toFloat(), py.toFloat(), -lowest), flat))
        }

        /** Lowest vertex height relative to the centre for a rotation (negative). */
        fun lowestOffset(rotation: Quat): Float = geometry.vertices.minOf { rotation.rotate(it).z } * scaleF

        /** Shifts a pose horizontally so no vertex crosses the rails or the lane walls. */
        fun clampToRails(pose: DiePose): DiePose {
            var minVx = Float.MAX_VALUE
            var maxVx = -Float.MAX_VALUE
            var minVy = Float.MAX_VALUE
            var maxVy = -Float.MAX_VALUE
            geometry.vertices.forEach { vertex ->
                val p = pose.rotation.rotate(vertex) * scaleF + pose.position
                minVx = min(minVx, p.x); maxVx = max(maxVx, p.x)
                minVy = min(minVy, p.y); maxVy = max(maxVy, p.y)
            }
            val dx = when {
                minVx < minX -> (minX - minVx).toFloat()
                maxVx > maxX -> (maxX - maxVx).toFloat()
                else -> 0f
            }
            val dy = when {
                minVy < minY -> (minY - minVy).toFloat()
                maxVy > maxY -> (maxY - maxVy).toFloat()
                else -> 0f
            }
            return if (dx == 0f && dy == 0f) pose else pose.copy(position = pose.position + Vec3(dx, dy, 0f))
        }
    }

    private class Contact {
        lateinit var a: Body
        var b: Body? = null
        var nx = 0.0; var ny = 0.0; var nz = 0.0
        var rax = 0.0; var ray = 0.0; var raz = 0.0
        var rbx = 0.0; var rby = 0.0; var rbz = 0.0
        var t1x = 0.0; var t1y = 0.0; var t1z = 0.0
        var t2x = 0.0; var t2y = 0.0; var t2z = 0.0
        var massN = 0.0; var massT1 = 0.0; var massT2 = 0.0
        var target = 0.0
        var friction = 0.0
        var jn = 0.0; var jt1 = 0.0; var jt2 = 0.0
    }

    private class World(private val bodies: List<Body>) {
        private val contacts = Array(MAX_CONTACTS) { Contact() }
        private var contactCount = 0
        private val scratch = DoubleArray(3)
        private val scratchB = DoubleArray(3)
        private val dynamic = bodies.filterNot { it.isStatic }

        fun step(time: Double) {
            dynamic.forEach { body ->
                if (!body.asleep) body.vz -= GRAVITY * DT
                body.floorContacts = 0
            }
            contactCount = 0
            dynamic.forEach { body -> if (!body.asleep) collidePlanes(body) }
            for (i in bodies.indices) for (j in i + 1 until bodies.size) {
                val a = bodies[i]
                val b = bodies[j]
                if ((a.isStatic || a.asleep) && (b.isStatic || b.asleep)) continue
                if (!shouldResolveDiceCollision(a.lane, b.lane)) continue
                collidePair(a, b)
            }
            repeat(ITERATIONS) {
                for (index in 0 until contactCount) solve(contacts[index])
            }
            dynamic.forEach { body ->
                if (body.asleep) return@forEach
                body.integrate(DT)
                body.refreshWorld()
                projectPlanes(body)
            }
            for (i in bodies.indices) for (j in i + 1 until bodies.size) {
                val a = bodies[i]
                val b = bodies[j]
                if ((a.isStatic || a.asleep) && (b.isStatic || b.asleep)) continue
                if (!shouldResolveDiceCollision(a.lane, b.lane)) continue
                separatePair(a, b)
            }
            dynamic.forEach { body -> if (!body.asleep) dampAndSleep(body, time) }
        }

        private fun collidePlanes(body: Body) {
            val w = body.world
            // Lane separators are invisible: they absorb the dice softly instead of bouncing them.
            val laneRestitution = if (body.lane != null) LANE_RESTITUTION else WALL_RESTITUTION
            for (i in 0 until body.vertexCount) {
                val x = w[i * 3]; val y = w[i * 3 + 1]; val z = w[i * 3 + 2]
                if (z < CONTACT_MARGIN) {
                    body.floorContacts++
                    addPlaneContact(body, x, y, z, 0.0, 0.0, 1.0, z, FLOOR_FRICTION, FLOOR_RESTITUTION)
                }
                if (x - body.minX < CONTACT_MARGIN) addPlaneContact(body, x, y, z, 1.0, 0.0, 0.0, x - body.minX, WALL_FRICTION, WALL_RESTITUTION)
                if (body.maxX - x < CONTACT_MARGIN) addPlaneContact(body, x, y, z, -1.0, 0.0, 0.0, body.maxX - x, WALL_FRICTION, WALL_RESTITUTION)
                if (y - body.minY < CONTACT_MARGIN) addPlaneContact(body, x, y, z, 0.0, 1.0, 0.0, y - body.minY, WALL_FRICTION, laneRestitution)
                if (body.maxY - y < CONTACT_MARGIN) addPlaneContact(body, x, y, z, 0.0, -1.0, 0.0, body.maxY - y, WALL_FRICTION, laneRestitution)
            }
        }

        private fun addPlaneContact(
            body: Body, x: Double, y: Double, z: Double,
            nx: Double, ny: Double, nz: Double, gap: Double,
            friction: Double, restitution: Double,
        ) {
            if (contactCount >= MAX_CONTACTS) return
            val c = contacts[contactCount++]
            c.a = body
            c.b = null
            setup(c, nx, ny, nz, x - body.px, y - body.py, z - body.pz, 0.0, 0.0, 0.0, gap, friction, restitution)
        }

        /** Vertices of either die that entered the other's convex hull become contacts. */
        private fun collidePair(a: Body, b: Body) {
            val dx = b.px - a.px; val dy = b.py - a.py; val dz = b.pz - a.pz
            val reach = a.boundRadius + b.boundRadius + CONTACT_MARGIN
            if (dx * dx + dy * dy + dz * dz > reach * reach) return
            val before = contactCount
            vertexContacts(owner = a, hull = b)
            vertexContacts(owner = b, hull = a)
            if (contactCount > before) slideOff(a, b)
        }

        /**
         * Real dice have rounded edges and slippery faces: one balanced on another slides off
         * instead of staying stacked.
         */
        private fun slideOff(a: Body, b: Body) {
            val upper = if (a.pz >= b.pz) a else b
            val lower = if (upper === a) b else a
            if (upper.isStatic || upper.asleep) return
            if (upper.pz - lower.pz < 0.55 * (upper.restHeight + lower.restHeight)) return
            val dx = upper.px - lower.px
            val dy = upper.py - lower.py
            val planar = sqrt(dx * dx + dy * dy)
            val ux = if (planar > 1e-4) dx / planar else 1.0
            val uy = if (planar > 1e-4) dy / planar else 0.0
            upper.vx += ux * STACK_SLIDE * DT
            upper.vy += uy * STACK_SLIDE * DT
        }

        private fun vertexContacts(owner: Body, hull: Body) {
            val w = owner.world
            for (i in 0 until owner.vertexCount) {
                val x = w[i * 3]; val y = w[i * 3 + 1]; val z = w[i * 3 + 2]
                var best = -Double.MAX_VALUE
                var bestFace = -1
                var inside = true
                for (f in 0 until hull.faceCount) {
                    val fx = hull.faceWorld[f * 3]; val fy = hull.faceWorld[f * 3 + 1]; val fz = hull.faceWorld[f * 3 + 2]
                    val distance = fx * (x - hull.px) + fy * (y - hull.py) + fz * (z - hull.pz) - hull.faceOffset[f]
                    if (distance > CONTACT_MARGIN) {
                        inside = false
                        break
                    }
                    if (distance > best) {
                        best = distance
                        bestFace = f
                    }
                }
                if (!inside || bestFace < 0 || contactCount >= MAX_CONTACTS) continue
                val nx = hull.faceWorld[bestFace * 3]; val ny = hull.faceWorld[bestFace * 3 + 1]; val nz = hull.faceWorld[bestFace * 3 + 2]
                // Something hit a resting die hard enough: let it react.
                val approach = (owner.vx - hull.vx) * nx + (owner.vy - hull.vy) * ny + (owner.vz - hull.vz) * nz
                if (approach < -WAKE_SPEED) {
                    owner.wake()
                    hull.wake()
                }
                val c = contacts[contactCount++]
                c.a = owner
                c.b = hull
                setup(
                    c, nx, ny, nz,
                    x - owner.px, y - owner.py, z - owner.pz,
                    x - hull.px, y - hull.py, z - hull.pz,
                    best, DIE_FRICTION, DIE_RESTITUTION,
                )
            }
        }

        private fun setup(
            c: Contact,
            nx: Double, ny: Double, nz: Double,
            rax: Double, ray: Double, raz: Double,
            rbx: Double, rby: Double, rbz: Double,
            gap: Double, friction: Double, restitution: Double,
        ) {
            c.nx = nx; c.ny = ny; c.nz = nz
            c.rax = rax; c.ray = ray; c.raz = raz
            c.rbx = rbx; c.rby = rby; c.rbz = rbz
            // Tangent basis.
            if (abs(nx) < 0.57) {
                // t1 = n × X
                c.t1x = 0.0; c.t1y = nz; c.t1z = -ny
            } else {
                // t1 = n × Y
                c.t1x = -nz; c.t1y = 0.0; c.t1z = nx
            }
            val tl = sqrt(c.t1x * c.t1x + c.t1y * c.t1y + c.t1z * c.t1z)
            c.t1x /= tl; c.t1y /= tl; c.t1z /= tl
            c.t2x = ny * c.t1z - nz * c.t1y
            c.t2y = nz * c.t1x - nx * c.t1z
            c.t2z = nx * c.t1y - ny * c.t1x
            c.massN = 1.0 / effectiveMass(c, nx, ny, nz)
            c.massT1 = 1.0 / effectiveMass(c, c.t1x, c.t1y, c.t1z)
            c.massT2 = 1.0 / effectiveMass(c, c.t2x, c.t2y, c.t2z)
            val un = relativeVelocity(c, nx, ny, nz)
            c.target = when {
                un < -RESTITUTION_THRESHOLD -> -restitution * un
                gap > 0.0 -> -gap / DT
                else -> 0.0
            }
            c.friction = friction
            c.jn = 0.0; c.jt1 = 0.0; c.jt2 = 0.0
        }

        private fun effectiveMass(c: Contact, dx: Double, dy: Double, dz: Double): Double {
            val a = c.a
            var k = a.activeInvMass
            // (ra × d)
            var cx = c.ray * dz - c.raz * dy
            var cy = c.raz * dx - c.rax * dz
            var cz = c.rax * dy - c.ray * dx
            a.invInertiaWorld(cx, cy, cz, scratch)
            k += cx * scratch[0] + cy * scratch[1] + cz * scratch[2]
            c.b?.let { b ->
                k += b.activeInvMass
                cx = c.rby * dz - c.rbz * dy
                cy = c.rbz * dx - c.rbx * dz
                cz = c.rbx * dy - c.rby * dx
                b.invInertiaWorld(cx, cy, cz, scratch)
                k += cx * scratch[0] + cy * scratch[1] + cz * scratch[2]
            }
            return k.coerceAtLeast(1e-9)
        }

        private fun relativeVelocity(c: Contact, dx: Double, dy: Double, dz: Double): Double {
            val a = c.a
            var ux = a.vx + (a.wy * c.raz - a.wz * c.ray)
            var uy = a.vy + (a.wz * c.rax - a.wx * c.raz)
            var uz = a.vz + (a.wx * c.ray - a.wy * c.rax)
            c.b?.let { b ->
                ux -= b.vx + (b.wy * c.rbz - b.wz * c.rby)
                uy -= b.vy + (b.wz * c.rbx - b.wx * c.rbz)
                uz -= b.vz + (b.wx * c.rby - b.wy * c.rbx)
            }
            return ux * dx + uy * dy + uz * dz
        }

        private fun applyImpulse(c: Contact, jx: Double, jy: Double, jz: Double) {
            val a = c.a
            val ma = a.activeInvMass
            a.vx += jx * ma; a.vy += jy * ma; a.vz += jz * ma
            a.invInertiaWorld(c.ray * jz - c.raz * jy, c.raz * jx - c.rax * jz, c.rax * jy - c.ray * jx, scratch)
            a.wx += scratch[0]; a.wy += scratch[1]; a.wz += scratch[2]
            c.b?.let { b ->
                val mb = b.activeInvMass
                b.vx -= jx * mb; b.vy -= jy * mb; b.vz -= jz * mb
                b.invInertiaWorld(c.rby * jz - c.rbz * jy, c.rbz * jx - c.rbx * jz, c.rbx * jy - c.rby * jx, scratchB)
                b.wx -= scratchB[0]; b.wy -= scratchB[1]; b.wz -= scratchB[2]
            }
        }

        private fun solve(c: Contact) {
            val un = relativeVelocity(c, c.nx, c.ny, c.nz)
            val newJn = (c.jn + (c.target - un) * c.massN).coerceAtLeast(0.0)
            val dn = newJn - c.jn
            c.jn = newJn
            if (dn != 0.0) applyImpulse(c, c.nx * dn, c.ny * dn, c.nz * dn)
            val limit = c.friction * c.jn
            val u1 = relativeVelocity(c, c.t1x, c.t1y, c.t1z)
            val newJt1 = (c.jt1 - u1 * c.massT1).coerceIn(-limit, limit)
            val d1 = newJt1 - c.jt1
            c.jt1 = newJt1
            if (d1 != 0.0) applyImpulse(c, c.t1x * d1, c.t1y * d1, c.t1z * d1)
            val u2 = relativeVelocity(c, c.t2x, c.t2y, c.t2z)
            val newJt2 = (c.jt2 - u2 * c.massT2).coerceIn(-limit, limit)
            val d2 = newJt2 - c.jt2
            c.jt2 = newJt2
            if (d2 != 0.0) applyImpulse(c, c.t2x * d2, c.t2y * d2, c.t2z * d2)
        }

        /** Positional projection: never let a vertex stay inside the felt or a rail. */
        private fun projectPlanes(body: Body) {
            val w = body.world
            var floor = 0.0; var left = 0.0; var right = 0.0; var bottom = 0.0; var top = 0.0
            for (i in 0 until body.vertexCount) {
                floor = min(floor, w[i * 3 + 2])
                left = min(left, w[i * 3] - body.minX)
                right = min(right, body.maxX - w[i * 3])
                bottom = min(bottom, w[i * 3 + 1] - body.minY)
                top = min(top, body.maxY - w[i * 3 + 1])
            }
            val dz = -floor
            val dx = -left + right
            val dy = -bottom + top
            if (dx != 0.0 || dy != 0.0 || dz != 0.0) body.translate(dx, dy, dz)
        }

        private fun separatePair(a: Body, b: Body) {
            val dx = b.px - a.px; val dy = b.py - a.py; val dz = b.pz - a.pz
            val reach = a.boundRadius + b.boundRadius
            if (dx * dx + dy * dy + dz * dz > reach * reach) return
            val first = deepest(owner = a, hull = b)
            val second = deepest(owner = b, hull = a)
            // Push along the deeper of the two penetrations (normal points from hull to owner).
            val (depth, nx, ny, nz, ownerIsA) = if (first[0] <= second[0]) {
                Quintuple(first[0], first[1], first[2], first[3], true)
            } else Quintuple(second[0], second[1], second[2], second[3], false)
            if (depth >= -0.0005) return
            val wa = a.activeInvMass
            val wb = b.activeInvMass
            val total = wa + wb
            if (total <= 0.0) return
            val push = (-depth - 0.0005) * 0.8
            val sign = if (ownerIsA) 1.0 else -1.0
            a.translate(nx * push * sign * wa / total, ny * push * sign * wa / total, nz * push * sign * wa / total)
            b.translate(-nx * push * sign * wb / total, -ny * push * sign * wb / total, -nz * push * sign * wb / total)
        }

        /** [depth (negative = penetration), nx, ny, nz] of the deepest owner vertex inside hull. */
        private fun deepest(owner: Body, hull: Body): DoubleArray {
            var deepestDepth = 0.0
            var bx = 0.0; var by = 0.0; var bz = 0.0
            val w = owner.world
            for (i in 0 until owner.vertexCount) {
                val x = w[i * 3]; val y = w[i * 3 + 1]; val z = w[i * 3 + 2]
                var best = -Double.MAX_VALUE
                var bestFace = -1
                for (f in 0 until hull.faceCount) {
                    val distance = hull.faceWorld[f * 3] * (x - hull.px) + hull.faceWorld[f * 3 + 1] * (y - hull.py) +
                        hull.faceWorld[f * 3 + 2] * (z - hull.pz) - hull.faceOffset[f]
                    if (distance >= 0.0) {
                        bestFace = -1
                        break
                    }
                    if (distance > best) {
                        best = distance
                        bestFace = f
                    }
                }
                if (bestFace >= 0 && best < deepestDepth) {
                    deepestDepth = best
                    bx = hull.faceWorld[bestFace * 3]; by = hull.faceWorld[bestFace * 3 + 1]; bz = hull.faceWorld[bestFace * 3 + 2]
                }
            }
            return doubleArrayOf(deepestDepth, bx, by, bz)
        }

        private fun dampAndSleep(body: Body, time: Double) {
            var linear = exp(-LINEAR_DAMPING * DT)
            var angular = exp(-ANGULAR_DAMPING * DT)
            if (body.floorContacts > 0) angular *= exp(-ROLLING_DAMPING * DT)
            if (time > ASSIST_START_SECONDS) {
                // Felt eventually grabs every die: guarantees a short, bounded throw.
                val extra = exp(-(time - ASSIST_START_SECONDS) * ASSIST_RATE * DT)
                linear *= extra
                angular *= extra
            }
            body.vx *= linear; body.vy *= linear
            body.wx *= angular; body.wy *= angular; body.wz *= angular
            val speed2 = body.vx * body.vx + body.vy * body.vy + body.vz * body.vz
            val spin2 = body.wx * body.wx + body.wy * body.wy + body.wz * body.wz
            val resting = body.floorContacts >= 3 || (body.floorContacts >= 1 && time > ASSIST_START_SECONDS)
            if (resting && speed2 < SLEEP_LINEAR * SLEEP_LINEAR && spin2 < SLEEP_ANGULAR * SLEEP_ANGULAR) {
                body.quiet += DT
                if (body.quiet >= SLEEP_SECONDS) {
                    body.asleep = true
                    body.vx = 0.0; body.vy = 0.0; body.vz = 0.0
                    body.wx = 0.0; body.wy = 0.0; body.wz = 0.0
                }
            } else body.quiet = 0.0
        }
    }

    private data class Quintuple(val depth: Double, val nx: Double, val ny: Double, val nz: Double, val ownerIsA: Boolean)
}
