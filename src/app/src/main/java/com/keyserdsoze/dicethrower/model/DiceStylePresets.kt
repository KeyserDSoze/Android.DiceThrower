package com.keyserdsoze.dicethrower.model

/**
 * Immutable built-in palettes, not persisted user styles. A preset is copied into
 * the character-owned style library with a fresh ID before it is assigned to
 * Roll Parts, dice slots or candidate B. None of these choices affect dice RNG.
 */
data class DiceStylePreset(
    val key: String,
    val material: DiceMaterial,
    val primaryColorArgb: Int,
    val secondaryColorArgb: Int,
) {
    fun instantiate(characterId: String, id: String, name: String): DiceStyle = DiceStyle(
        id = id,
        characterId = characterId,
        name = name,
        material = material,
        primaryColorArgb = primaryColorArgb,
        secondaryColorArgb = secondaryColorArgb,
    )
}

object DiceStylePresets {
    val all: List<DiceStylePreset> = listOf(
        DiceStylePreset("arcane-blue", DiceMaterial.GLOSSY_RESIN, 0xFF2563EB.toInt(), 0xFFF6C453.toInt()),
        DiceStylePreset("inferno-forged", DiceMaterial.METAL, 0xFFBA3447.toInt(), 0xFFF59B42.toInt()),
        DiceStylePreset("emerald-aether", DiceMaterial.GEMSTONE, 0xFF0C9D75.toInt(), 0xFFE1D18A.toInt()),
        DiceStylePreset("violet-star", DiceMaterial.GEMSTONE, 0xFF8547D4.toInt(), 0xFF65E7DE.toInt()),
        DiceStylePreset("obsidian-rune", DiceMaterial.MATTE_RESIN, 0xFF20232B.toInt(), 0xFFE3AF51.toInt()),
        DiceStylePreset("lunar-frost", DiceMaterial.GEMSTONE, 0xFFB5DEED.toInt(), 0xFF315E8B.toInt()),
        DiceStylePreset("sunlit-brass", DiceMaterial.METAL, 0xFFD5A548.toInt(), 0xFF79441B.toInt()),
        DiceStylePreset("celestial-pearl", DiceMaterial.GLOSSY_RESIN, 0xFFF2E9E2.toInt(), 0xFF8261AD.toInt()),
        DiceStylePreset("deep-ocean", DiceMaterial.MATTE_RESIN, 0xFF1B3869.toInt(), 0xFF44C3C2.toInt()),
        DiceStylePreset("ancient-copper", DiceMaterial.METAL, 0xFF9C553A.toInt(), 0xFFF5D0A0.toInt()),
    )

    fun find(key: String): DiceStylePreset? = all.firstOrNull { it.key == key }
}
