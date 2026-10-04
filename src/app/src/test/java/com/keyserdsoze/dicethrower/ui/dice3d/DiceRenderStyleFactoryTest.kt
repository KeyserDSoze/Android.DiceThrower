package com.keyserdsoze.dicethrower.ui.dice3d

import com.keyserdsoze.dicethrower.dice.DiceAppearanceResolver
import com.keyserdsoze.dicethrower.dice.ResolvedDiceAppearance
import com.keyserdsoze.dicethrower.model.DiceMaterial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DiceRenderStyleFactoryTest {
    @Test
    fun rendererUsesBothConfiguredColors() {
        val style = DiceRenderStyleFactory.create(
            appearance(
                material = DiceMaterial.GLOSSY_RESIN,
                primary = 0xFF123456.toInt(),
                secondary = 0xFFABCDEF.toInt(),
            ),
        )

        assertColor(style.primary, red = 0x12, green = 0x34, blue = 0x56)
        assertColor(style.secondary, red = 0xAB, green = 0xCD, blue = 0xEF)
    }

    @Test
    fun everyMaterialHasADistinctLightingResponse() {
        val profiles = DiceMaterial.entries.associateWith { material ->
            DiceRenderStyleFactory.create(appearance(material = material)).lighting
        }

        assertEquals(DiceMaterial.entries.size, profiles.values.toSet().size)
        assertNotEquals(profiles.getValue(DiceMaterial.GLOSSY_RESIN), profiles.getValue(DiceMaterial.MATTE_RESIN))
        assertNotEquals(profiles.getValue(DiceMaterial.METAL), profiles.getValue(DiceMaterial.GEMSTONE))
    }

    @Test
    fun missingAppearanceUsesDeterministicBuiltInFallback() {
        val style = DiceRenderStyleFactory.create(null)

        val expectedPrimary = DiceAppearanceResolver.defaultPrimaryColorArgb
        val expectedSecondary = DiceAppearanceResolver.defaultSecondaryColorArgb
        assertColor(
            style.primary,
            red = (expectedPrimary ushr 16) and 0xFF,
            green = (expectedPrimary ushr 8) and 0xFF,
            blue = expectedPrimary and 0xFF,
        )
        assertColor(
            style.secondary,
            red = (expectedSecondary ushr 16) and 0xFF,
            green = (expectedSecondary ushr 8) and 0xFF,
            blue = expectedSecondary and 0xFF,
        )
        assertEquals(
            DiceRenderStyleFactory.create(appearance(material = DiceMaterial.GLOSSY_RESIN)).lighting,
            style.lighting,
        )
    }

    private fun appearance(
        material: DiceMaterial,
        primary: Int = 0xFF2563EB.toInt(),
        secondary: Int = 0xFFF6C453.toInt(),
    ) = ResolvedDiceAppearance(
        slotKey = "0:0",
        componentIndex = 0,
        dieIndex = 0,
        sourceStyleId = "style",
        material = material,
        primaryColorArgb = primary,
        secondaryColorArgb = secondary,
    )

    private fun assertColor(color: RenderColor, red: Int, green: Int, blue: Int) {
        assertEquals(red / 255f, color.red, 0.0001f)
        assertEquals(green / 255f, color.green, 0.0001f)
        assertEquals(blue / 255f, color.blue, 0.0001f)
        assertEquals(1f, color.alpha, 0.0001f)
    }
}
