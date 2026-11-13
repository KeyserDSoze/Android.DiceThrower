package com.keyserdsoze.dicethrower.dice

/** Projects sampled logical dice onto a bounded table without sampling or changing results. */
internal object DiceVisualProjection {
    const val MAX_VISIBLE_DICE = 12

    data class Face(
        val componentIndex: Int,
        val rollIndex: Int,
        val logicalDieIndex: Int,
        val sides: Int,
        val value: Int,
        /** The d10 anchors 1–10 are painted one lower (0–9) only for percentile units. */
        val percentileUnits: Boolean = false,
    )

    data class Layout(
        val faces: List<Face>,
        /** Number of rendered bodies, after expanding any persistent percentile pairs. */
        val persistentDiceCount: Int,
    )

    fun project(result: DiceRollResult, persistentLogicalDiceCount: Int = 0): Layout {
        var logicalDieIndex = 0
        val faces = buildList {
            for ((componentIndex, component) in result.components.withIndex()) {
                for ((rollIndex, value) in component.rolls.withIndex()) {
                    val logicalIndex = logicalDieIndex++
                    if (component.sides !in DiceExpression.supportedSides) continue
                    val faceCount = if (component.sides == 100) 2 else 1
                    // Keep a visible prefix of complete logical dice. Never split a percentile
                    // pair at the table boundary or show its units as an unrelated later die.
                    if (size + faceCount > MAX_VISIBLE_DICE) return@buildList
                    add(Face(componentIndex, rollIndex, logicalIndex, component.sides, value))
                    if (component.sides == 100) {
                        val units = value % 10
                        add(Face(
                            componentIndex, rollIndex, logicalIndex, sides = 10,
                            value = units + 1, percentileUnits = true,
                        ))
                    }
                }
            }
        }
        return Layout(faces, faces.count { it.logicalDieIndex < persistentLogicalDiceCount })
    }

    /** Capacity counts rendered bodies, not logical samples: a d100 needs two spaces. */
    fun diceCount(components: List<DiceComponent>): Int = components.sumOf { component ->
        component.rolls.size * when (component.sides) {
            100 -> 2
            in DiceExpression.supportedSides -> 1
            else -> 0
        }
    }
}
