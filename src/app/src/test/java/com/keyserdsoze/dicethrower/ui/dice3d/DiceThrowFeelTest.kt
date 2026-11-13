package com.keyserdsoze.dicethrower.ui.dice3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Statistical guard for the feel of a throw over hundreds of seeded throws: short but not
 * abrupt, dice coming to rest in the visible middle of the table, never interpenetrating.
 */
class DiceThrowFeelTest {
    private fun percentile(sorted: List<Float>, q: Float) = sorted[((sorted.size - 1) * q).toInt()]

    @Test
    fun throwsFeelNaturalAcrossManySeeds() {
        val durations = mutableListOf<Float>()
        val finalY = mutableListOf<Float>()
        var overlaps = 0
        var highest = 0f
        listOf(1, 2, 3, 5, 8, 12).forEach { count ->
            repeat(30) { round ->
                val specs = List(count) { DieThrowSpec(listOf(20, 6, 8, 12, 10, 4, 100)[(it + round) % 7], 1) }
                val scale = DiceTableViewport.dieScaleFor(count)
                val baked = DiceThrowSimulator.bake(specs, round * 7919L + count, scale)
                durations += baked.durationSeconds
                var t = 0f
                while (t < baked.durationSeconds) {
                    specs.indices.forEach { highest = maxOf(highest, baked.pose(it, t).position.z) }
                    t += 0.05f
                }
                val finals = specs.indices.map { baked.finalPose(it) }
                finals.forEach { finalY += it.position.y }
                finals.forEachIndexed { i, a ->
                    finals.drop(i + 1).forEachIndexed { offset, b ->
                        val j = i + 1 + offset
                        val minimum = (DieGeometries.forSides(specs[i].sides).inradius +
                            DieGeometries.forSides(specs[j].sides).inradius) * scale
                        if ((a.position - b.position).length() < minimum * 0.97f) overlaps++
                    }
                }
            }
        }
        durations.sort()
        finalY.sort()
        println("duration p10=${percentile(durations, .1f)} p50=${percentile(durations, .5f)} p90=${percentile(durations, .9f)}")
        println("final y p05=${percentile(finalY, .05f)} p50=${percentile(finalY, .5f)} p95=${percentile(finalY, .95f)} highest=$highest")
        assertEquals("dice interpenetrating at rest", 0, overlaps)
        assertTrue("throws too abrupt", percentile(durations, .1f) >= 0.9f)
        assertTrue("throws too long", percentile(durations, .9f) <= 3.0f)
        assertTrue("dice should rest mid-table", percentile(finalY, .5f) in -1.5f..2.5f)
        val hidden = finalY.count { it < -DiceTableViewport.HALF_HEIGHT * 0.31f }
        println("dice resting under the results panel: $hidden of ${finalY.size}")
        assertTrue("dice hidden under the results panel: $hidden", hidden <= finalY.size / 100)
        assertTrue("dice crowding the top rail", percentile(finalY, .95f) <= 4.8f)
        assertTrue("dice must visibly fly", highest > 1.6f)
    }
}
