package com.keyserdsoze.dicethrower.dice

import com.keyserdsoze.dicethrower.model.CharacterProfile
import com.keyserdsoze.dicethrower.model.DiceAppearanceMode
import com.keyserdsoze.dicethrower.model.DiceMaterial
import com.keyserdsoze.dicethrower.model.DiceStyle
import com.keyserdsoze.dicethrower.model.RollDiceAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DiceAppearanceResolverTest {
    private val character = CharacterProfile(
        id = "character",
        name = "Alyndra",
        defaultDiceStyleId = "blue",
    )
    private val blue = DiceStyle(
        id = "blue",
        characterId = "character",
        name = "Arcane Blue",
        material = DiceMaterial.GLOSSY_RESIN,
        primaryColorArgb = 0xFF2563EB.toInt(),
        secondaryColorArgb = 0xFFF6C453.toInt(),
    )
    private val steel = DiceStyle(
        id = "steel",
        characterId = "character",
        name = "Steel",
        material = DiceMaterial.METAL,
        primaryColorArgb = 0xFF64748B.toInt(),
        secondaryColorArgb = 0xFFE2E8F0.toInt(),
        order = 1,
    )
    private val result = DiceRollResult(
        total = 18,
        components = listOf(
            DiceComponent(count = 3, sides = 6, sign = 1, rolls = listOf(2, 5, 4)),
            DiceComponent(count = 1, sides = 20, sign = 1, rolls = listOf(7)),
        ),
        constantTotal = 0,
    )

    @Test
    fun characterDefaultAppliesToEveryDie() {
        val resolved = resolve(RollDiceAppearance())

        assertEquals(listOf("blue", "blue", "blue", "blue"), resolved.map { it.sourceStyleId })
        assertEquals(listOf("0:0", "0:1", "0:2", "1:0"), resolved.map { it.slotKey })
    }

    @Test
    fun uniformUsesOneSelectedStyle() {
        val resolved = resolve(
            RollDiceAppearance(
                mode = DiceAppearanceMode.UNIFORM,
                styleId = "steel",
            ),
        )

        assertTrue(resolved.all { it.sourceStyleId == "steel" && it.material == DiceMaterial.METAL })
    }

    @Test
    fun perDieUsesOverridesAndFallsBackToCharacterDefault() {
        val resolved = resolve(
            RollDiceAppearance(
                mode = DiceAppearanceMode.PER_DIE,
                perDieStyleIds = mapOf("0:1" to "steel", "1:0" to "steel"),
            ),
        )

        assertEquals(listOf("blue", "steel", "blue", "steel"), resolved.map { it.sourceStyleId })
    }

    @Test
    fun expressionChangesPreserveCompatiblePerDieSlotsAndDropObsoleteOnes() {
        val original = DiceAppearanceResolver.slotsFor(DiceExpression.parse("4d6+1d20"))
        assertEquals(listOf("0:0", "0:1", "0:2", "0:3", "1:0"), original.map { it.key })

        val appearance = RollDiceAppearance(
            mode = DiceAppearanceMode.PER_DIE,
            perDieStyleIds = original.associate { it.key to "steel" },
        )
        val changed = DiceAppearanceResolver.slotsFor(DiceExpression.parse("2d6+1d20"))
        val reconciled = DiceAppearanceResolver.reconcileSlots(appearance, changed)

        assertEquals(listOf("0:0", "0:1", "1:0"), changed.map { it.key })
        assertEquals(setOf("0:0", "0:1", "1:0"), reconciled.perDieStyleIds.keys)
        assertEquals(DiceAppearanceMode.PER_DIE, reconciled.mode)
    }

    @Test
    fun visualSlotsIgnoreConstantsAndExposeDieShape() {
        val slots = DiceAppearanceResolver.slotsFor(DiceExpression.parse("4d6+2+1d20-3"))

        assertEquals(listOf(6, 6, 6, 6, 20), slots.map { it.sides })
        assertEquals(listOf(0, 1, 2, 3, 0), slots.map { it.dieIndex })
    }

    @Test
    fun randomUniformChoosesOnceForWholeThrow() {
        val resolved = resolve(
            RollDiceAppearance(
                mode = DiceAppearanceMode.RANDOM_UNIFORM,
                randomStyleIds = listOf("blue", "steel"),
            ),
            random = Random(12),
        )

        assertEquals(1, resolved.map { it.sourceStyleId }.toSet().size)
        assertTrue(resolved.first().sourceStyleId in setOf("blue", "steel"))
    }

    @Test
    fun randomPerDieCanMixStylesWithoutAffectingSlots() {
        val manyDice = DiceRollResult(
            total = 42,
            components = listOf(
                DiceComponent(count = 12, sides = 6, sign = 1, rolls = List(12) { 3 }),
            ),
            constantTotal = 0,
        )
        val resolved = DiceAppearanceResolver.resolve(
            character = character,
            styles = listOf(blue, steel),
            appearance = RollDiceAppearance(
                mode = DiceAppearanceMode.RANDOM_PER_DIE,
                randomStyleIds = listOf("blue", "steel"),
            ),
            result = manyDice,
            random = Random(7),
        )

        assertEquals(12, resolved.size)
        assertTrue(resolved.mapNotNull { it.sourceStyleId }.all { it in setOf("blue", "steel") })
        assertTrue(resolved.map { it.sourceStyleId }.toSet().size > 1)
    }

    @Test
    fun missingCustomStylesUseBuiltInFallback() {
        val fallbackCharacter = character.copy(defaultDiceStyleId = null)
        val oneDie = result.copy(components = listOf(result.components.last()))
        val resolved = DiceAppearanceResolver.resolve(
            character = fallbackCharacter,
            styles = emptyList(),
            appearance = RollDiceAppearance(),
            result = oneDie,
            random = Random(1),
        ).single()

        assertNull(resolved.sourceStyleId)
        assertEquals(DiceMaterial.GLOSSY_RESIN, resolved.material)
        assertEquals(DiceAppearanceResolver.defaultPrimaryColorArgb, resolved.primaryColorArgb)
    }

    @Test
    fun resolvingRandomAppearanceDoesNotConsumeTheDiceEngineRandom() {
        val engineWithAppearance = Random(20261004)
        val engineWithoutAppearance = Random(20261004)
        val expression = DiceExpression.parse("3d6+1d20")

        val firstWithAppearance = expression.evaluate(engineWithAppearance)
        DiceAppearanceResolver.resolve(
            character = character,
            styles = listOf(blue, steel),
            appearance = RollDiceAppearance(mode = DiceAppearanceMode.RANDOM_PER_DIE),
            result = firstWithAppearance,
            random = Random(99),
        )
        val secondWithAppearance = expression.evaluate(engineWithAppearance)

        val firstWithoutAppearance = expression.evaluate(engineWithoutAppearance)
        val secondWithoutAppearance = expression.evaluate(engineWithoutAppearance)

        assertEquals(firstWithoutAppearance, firstWithAppearance)
        assertEquals(secondWithoutAppearance, secondWithAppearance)
    }

    private fun resolve(
        appearance: RollDiceAppearance,
        random: Random = Random(1),
    ): List<ResolvedDiceAppearance> = DiceAppearanceResolver.resolve(
        character = character,
        styles = listOf(blue, steel),
        appearance = appearance,
        result = result,
        random = random,
    )
}
