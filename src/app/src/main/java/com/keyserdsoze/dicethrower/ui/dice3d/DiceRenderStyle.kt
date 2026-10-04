package com.keyserdsoze.dicethrower.ui.dice3d

import com.keyserdsoze.dicethrower.dice.DiceAppearanceResolver
import com.keyserdsoze.dicethrower.dice.ResolvedDiceAppearance
import com.keyserdsoze.dicethrower.model.DiceMaterial

internal data class RenderColor(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float,
)

internal data class MaterialLighting(
    val ambient: Float,
    val diffuse: Float,
    val specular: Float,
    val shininess: Float,
    val rim: Float,
    val accentMix: Float,
    val innerGlow: Float,
)

internal data class DiceRenderStyle(
    val primary: RenderColor,
    val secondary: RenderColor,
    val lighting: MaterialLighting,
)

internal object DiceRenderStyleFactory {
    fun create(appearance: ResolvedDiceAppearance?): DiceRenderStyle {
        val material = appearance?.material ?: DiceMaterial.GLOSSY_RESIN
        return DiceRenderStyle(
            primary = argbToColor(appearance?.primaryColorArgb ?: DiceAppearanceResolver.defaultPrimaryColorArgb),
            secondary = argbToColor(appearance?.secondaryColorArgb ?: DiceAppearanceResolver.defaultSecondaryColorArgb),
            lighting = materialLighting(material),
        )
    }

    private fun argbToColor(argb: Int): RenderColor = RenderColor(
        red = ((argb ushr 16) and 0xFF) / 255f,
        green = ((argb ushr 8) and 0xFF) / 255f,
        blue = (argb and 0xFF) / 255f,
        alpha = ((argb ushr 24) and 0xFF) / 255f,
    )

    private fun materialLighting(material: DiceMaterial): MaterialLighting = when (material) {
        DiceMaterial.GLOSSY_RESIN -> MaterialLighting(
            ambient = 0.24f,
            diffuse = 0.72f,
            specular = 0.78f,
            shininess = 34f,
            rim = 0.18f,
            accentMix = 0.20f,
            innerGlow = 0.02f,
        )
        DiceMaterial.MATTE_RESIN -> MaterialLighting(
            ambient = 0.31f,
            diffuse = 0.79f,
            specular = 0.10f,
            shininess = 7f,
            rim = 0.08f,
            accentMix = 0.16f,
            innerGlow = 0f,
        )
        DiceMaterial.METAL -> MaterialLighting(
            ambient = 0.18f,
            diffuse = 0.55f,
            specular = 1.00f,
            shininess = 58f,
            rim = 0.40f,
            accentMix = 0.31f,
            innerGlow = 0f,
        )
        DiceMaterial.GEMSTONE -> MaterialLighting(
            ambient = 0.18f,
            diffuse = 0.48f,
            specular = 0.70f,
            shininess = 30f,
            rim = 0.68f,
            accentMix = 0.42f,
            innerGlow = 0.24f,
        )
    }
}
