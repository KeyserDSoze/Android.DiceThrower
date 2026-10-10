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
        subgroupIds: Collection<String>? = null,
    ): RollDiceAppearance {
        val validKeys = slots.mapTo(mutableSetOf()) { it.key }
        val validSubgroups = subgroupIds?.toSet()
        return appearance.copy(
            subgroupStyleIds = if (validSubgroups == null) {
                appearance.subgroupStyleIds
            } else {
                appearance.subgroupStyleIds.filterKeys(validSubgroups::contains)
            },
            perDieStyleIds = appearance.perDieStyleIds.filterKeys(validKeys::contains),
        )
    }

    fun resolve(
        character: CharacterProfile,
        styles: List<DiceStyle>,
        appearance: RollDiceAppearance,
        result: DiceRollResult,
        subgroupIdByComponentIndex: Map<Int, String> = emptyMap(),
        random: Random = Random(System.nanoTime()),
        /** Only components in logical candidate B receive this visual override. */
        secondaryCandidateComponentIndices: Set<Int> = emptySet(),
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
                        DiceAppearanceMode.PER_DIE -> {
                            val subgroupStyleId = subgroupIdByComponentIndex[componentIndex]
                                ?.let(appearance.subgroupStyleIds::get)
                            resolveStyle(appearance.perDieStyleIds[slot] ?: subgroupStyleId) ?: defaultStyle
                        }
                        DiceAppearanceMode.RANDOM_UNIFORM -> uniformRandomStyle
                        DiceAppearanceMode.RANDOM_PER_DIE -> randomPool().randomOrNull(random) ?: defaultStyle
                    }
                    val primary = style.toResolved(slot, componentIndex, dieIndex)
                    if (componentIndex !in secondaryCandidateComponentIndices) {
                        add(primary)
                    } else {
                        // Candidate identity and Part identity are independent visual axes.
                        // A full replacement here used to erase different Part styles in B.
                        val candidateB = resolveStyle(character.secondaryDiceStyleId)
                            ?.toResolved(slot, componentIndex, dieIndex)
                            ?: contrastingAlternative(defaultStyle.toResolved(slot, componentIndex, dieIndex))
                        val subgroupStyle = subgroupIdByComponentIndex[componentIndex]
                            ?.let(appearance.subgroupStyleIds::get)
                            ?.let(stylesById::get)
                        val specificStyle = appearance.mode == DiceAppearanceMode.PER_DIE &&
                            (resolveStyle(appearance.perDieStyleIds[slot]) != null || subgroupStyle != null)
                        add(if (specificStyle || appearance.mode == DiceAppearanceMode.RANDOM_PER_DIE) {
                            mergePartAndCandidateStyle(primary, candidateB)
                        } else candidateB)
                    }
                }
            }
        }
    }

    /**
     * Preserve a Part-specific palette while tinting it toward the B look.
     * Every RGB channel is a deterministic mixture of the Part's color and
     * the candidate's color: unlike a fixed B replacement, distinct Part
     * colors remain distinct within a B throw. The B material/secondary
     * accent keeps candidate identity legible in 3D.
     */
    private fun mergePartAndCandidateStyle(
        part: ResolvedDiceAppearance,
        candidateB: ResolvedDiceAppearance,
    ): ResolvedDiceAppearance {
        fun mix(a: Int, b: Int): Int {
            fun channel(shift: Int): Int =
                (((a ushr shift) and 0xFF) * 3 + ((b ushr shift) and 0xFF) * 2) / 5
            return (0xFF shl 24) or
                (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
        return part.copy(
            // Keep the user-selected Part style as the source identity.
            material = candidateB.material,
            primaryColorArgb = mix(part.primaryColorArgb, candidateB.primaryColorArgb),
            secondaryColorArgb = candidateB.secondaryColorArgb,
        )
    }

    /**
     * Two distinct premium looks without creating hidden persisted styles for new
     * characters. Use a second palette when the original is already violet.
     * Deterministic and completely independent of the logical roll's RNG.
     */
    private fun contrastingAlternative(original: ResolvedDiceAppearance): ResolvedDiceAppearance {
        val firstRgb = original.primaryColorArgb
        val violet = 0xFF8C52E5.toInt()
        val gold = 0xFFE9A942.toInt()
        fun distance(a: Int, b: Int): Int =
            kotlin.math.abs(((a ushr 16) and 0xFF) - ((b ushr 16) and 0xFF)) +
                kotlin.math.abs(((a ushr 8) and 0xFF) - ((b ushr 8) and 0xFF)) +
                kotlin.math.abs((a and 0xFF) - (b and 0xFF))
        val alternativePrimary = if (distance(firstRgb, violet) > distance(firstRgb, gold))
            violet else gold
        return original.copy(
            sourceStyleId = null,
            material = if (original.material == DiceMaterial.GEMSTONE) DiceMaterial.METAL
                else DiceMaterial.GEMSTONE,
            primaryColorArgb = alternativePrimary,
            secondaryColorArgb = if (alternativePrimary == violet) 0xFF67EBDA.toInt()
                else 0xFF173C64.toInt(),
        )
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
