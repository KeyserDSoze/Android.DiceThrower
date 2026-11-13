package com.keyserdsoze.dicethrower.ui.dice3d

import com.keyserdsoze.dicethrower.dice.DiceAppearanceResolver
import com.keyserdsoze.dicethrower.dice.ResolvedDiceAppearance
import com.keyserdsoze.dicethrower.model.DiceMaterial
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

internal data class RenderColor(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float,
) {
    /** sRGB → linear light, the space the dice shader lights and blends in. */
    fun linear(): FloatArray = floatArrayOf(toLinear(red), toLinear(green), toLinear(blue))

    /** WCAG relative luminance. */
    fun luminance(): Float = 0.2126f * toLinear(red) + 0.7152f * toLinear(green) + 0.0722f * toLinear(blue)

    private fun toLinear(channel: Float): Float =
        if (channel <= 0.04045f) channel / 12.92f else ((channel + 0.055f) / 1.055f).pow(2.4f)
}

/**
 * Physically inspired surface response. Values are shader inputs only; nothing here can
 * influence which value a die shows.
 */
internal data class MaterialLighting(
    val roughness: Float,
    val metalness: Float,
    val clearcoat: Float,
    /** Light scattered inside the resin, visible as a luminous body near thin edges. */
    val translucency: Float,
    /** Strength of the secondary-colour marbling suspended in the resin. */
    val swirl: Float,
    /** Secondary-colour core glow (gemstones). */
    val innerGlow: Float,
    val sparkle: Float,
)

internal data class DiceRenderStyle(
    val primary: RenderColor,
    val secondary: RenderColor,
    /** Numeral paint: the style's secondary colour when it reads well on the body. */
    val ink: RenderColor,
    val lighting: MaterialLighting,
)

internal object DiceRenderStyleFactory {
    private const val MIN_INK_CONTRAST = 2.0f

    fun create(appearance: ResolvedDiceAppearance?): DiceRenderStyle {
        val material = appearance?.material ?: DiceMaterial.GLOSSY_RESIN
        val primary = argbToColor(appearance?.primaryColorArgb ?: DiceAppearanceResolver.defaultPrimaryColorArgb)
        val secondary = argbToColor(appearance?.secondaryColorArgb ?: DiceAppearanceResolver.defaultSecondaryColorArgb)
        return DiceRenderStyle(
            primary = primary,
            secondary = secondary,
            ink = inkFor(primary, secondary),
            lighting = materialLighting(material),
        )
    }

    fun contrast(first: RenderColor, second: RenderColor): Float {
        val a = first.luminance()
        val b = second.luminance()
        return (max(a, b) + 0.05f) / (min(a, b) + 0.05f)
    }

    private fun inkFor(primary: RenderColor, secondary: RenderColor): RenderColor {
        if (contrast(primary, secondary) >= MIN_INK_CONTRAST) return secondary.copy(alpha = 1f)
        val ivory = RenderColor(0.97f, 0.95f, 0.90f, 1f)
        val graphite = RenderColor(0.07f, 0.07f, 0.09f, 1f)
        return if (contrast(primary, ivory) >= contrast(primary, graphite)) ivory else graphite
    }

    private fun argbToColor(argb: Int): RenderColor = RenderColor(
        red = ((argb ushr 16) and 0xFF) / 255f,
        green = ((argb ushr 8) and 0xFF) / 255f,
        blue = (argb and 0xFF) / 255f,
        alpha = ((argb ushr 24) and 0xFF) / 255f,
    )

    private fun materialLighting(material: DiceMaterial): MaterialLighting = when (material) {
        DiceMaterial.GLOSSY_RESIN -> MaterialLighting(
            roughness = 0.30f,
            metalness = 0f,
            clearcoat = 1f,
            translucency = 0.40f,
            swirl = 0.92f,
            innerGlow = 0f,
            sparkle = 0f,
        )
        DiceMaterial.MATTE_RESIN -> MaterialLighting(
            roughness = 0.78f,
            metalness = 0f,
            clearcoat = 0f,
            translucency = 0.12f,
            swirl = 0.70f,
            innerGlow = 0f,
            sparkle = 0f,
        )
        DiceMaterial.METAL -> MaterialLighting(
            roughness = 0.26f,
            metalness = 1f,
            clearcoat = 0f,
            translucency = 0f,
            swirl = 0f,
            innerGlow = 0f,
            sparkle = 0.15f,
        )
        DiceMaterial.GEMSTONE -> MaterialLighting(
            roughness = 0.08f,
            metalness = 0f,
            clearcoat = 1f,
            translucency = 0.85f,
            swirl = 0f,
            innerGlow = 0.55f,
            sparkle = 1f,
        )
    }
}
