package com.keyserdsoze.dicethrower.dice

import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.DiceAppearanceMode
import com.keyserdsoze.dicethrower.model.DiceMaterial
import com.keyserdsoze.dicethrower.model.DiceStyle
import com.keyserdsoze.dicethrower.model.RollDiceAppearance
import kotlin.random.Random

data class ResolvedDiceAppearance(
    val slotKey: String,
    val componentIndex: Int,
    val dieIndex: Int,
    val sourceStyleId: String?,
    val material: DiceMaterial,
    val primaryColorArgb: Int,
    val secondaryColorArgb: Int,
)

data class DiceAppearanceSlot(
    val key: String,
    val componentIndex: Int,
    val dieIndex: Int,
    val sides: Int,
)

object DiceAppearanceResolver {
    val defaultPrimaryColorArgb: Int = 0xFF2563EB.toInt()
    val defaultSecondaryColorArgb: Int = 0xFFF6C453.toInt()

    fun slotKey(componentIndex: Int, dieIndex: Int): String = "$componentIndex:$dieIndex"

    fun slotsFor(expression: DiceExpression): List<DiceAppearanceSlot> = buildList {
        expression.diceShape().forEachIndexed { componentIndex, component ->
            repeat(component.count) { dieIndex ->
                add(
                    DiceAppearanceSlot(
                        key = slotKey(componentIndex, dieIndex),
                        componentIndex = componentIndex,
                        dieIndex = dieIndex,
                        sides = component.sides,
                    ),
                )
            }
        }
    }

    fun reconcileSlots(
        appearance: RollDiceAppearance,
        slots: Collection<DiceAppearanceSlot>,
    ): RollDiceAppearance {
        val validKeys = slots.mapTo(mutableSetOf()) { it.key }
        return appearance.copy(
            perDieStyleIds = appearance.perDieStyleIds.filterKeys(validKeys::contains),
        )
    }

    fun resolve(
        character: CharacterProfile,
        styles: List<DiceStyle>,
        appearance: RollDiceAppearance,
        result: DiceRollResult,
        random: Random = Random(System.nanoTime()),
    ): List<ResolvedDiceAppearance> {
        val ownedStyles = styles
            .filter { it.characterId == character.id }
            .sortedBy { it.order }
        val stylesById = ownedStyles.associateBy { it.id }
        val defaultStyle = character.defaultDiceStyleId?.let(stylesById::get)

        fun resolveStyle(styleId: String?): DiceStyle? = styleId?.let(stylesById::get)
        fun randomPool(): List<DiceStyle> {
            val explicit = appearance.randomStyleIds.mapNotNull(stylesById::get).distinctBy { it.id }
            return if (explicit.isNotEmpty()) explicit else ownedStyles
        }

        val uniformRandomStyle = if (appearance.mode == DiceAppearanceMode.RANDOM_UNIFORM) {
            randomPool().randomOrNull(random) ?: defaultStyle
        } else null

        return buildList {
            result.components.forEachIndexed { componentIndex, component ->
                component.rolls.indices.forEach { dieIndex ->
                    val slot = slotKey(componentIndex, dieIndex)
                    val style = when (appearance.mode) {
                        DiceAppearanceMode.CHARACTER_DEFAULT -> defaultStyle
                        DiceAppearanceMode.UNIFORM -> resolveStyle(appearance.styleId) ?: defaultStyle
                        DiceAppearanceMode.PER_DIE -> resolveStyle(appearance.perDieStyleIds[slot]) ?: defaultStyle
                        DiceAppearanceMode.RANDOM_UNIFORM -> uniformRandomStyle
                        DiceAppearanceMode.RANDOM_PER_DIE -> randomPool().randomOrNull(random) ?: defaultStyle
                    }
                    add(style.toResolved(slot, componentIndex, dieIndex))
                }
            }
        }
    }

    private fun DiceStyle?.toResolved(
        slot: String,
        componentIndex: Int,
        dieIndex: Int,
    ): ResolvedDiceAppearance = ResolvedDiceAppearance(
        slotKey = slot,
        componentIndex = componentIndex,
        dieIndex = dieIndex,
        sourceStyleId = this?.id,
        material = this?.material ?: DiceMaterial.GLOSSY_RESIN,
        primaryColorArgb = this?.primaryColorArgb ?: defaultPrimaryColorArgb,
        secondaryColorArgb = this?.secondaryColorArgb ?: defaultSecondaryColorArgb,
    )

    private fun <T> List<T>.randomOrNull(random: Random): T? =
        if (isEmpty()) null else this[random.nextInt(size)]
}
