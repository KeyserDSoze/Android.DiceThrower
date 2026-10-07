package com.keyserdsoze.dicethrower.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ConflictPolicy { ASK, LATEST_WINS }

enum class RollButtonPosition {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT,
}

enum class LevelRuleKind {
    FROM_LEVEL,
    EVERY_LEVELS,
}

enum class RollSubgroupOperator {
    ADD,
    SUBTRACT,
}

enum class DiceMaterial {
    GLOSSY_RESIN,
    MATTE_RESIN,
    METAL,
    GEMSTONE,
}

enum class DiceTableTheme {
    ARCANE,
    OAK,
    EMERALD,
    OBSIDIAN,
    TAVERN_WOOD,
    DUNGEON_STONE,
    ELVEN_GROVE,
    FROZEN_REALM,
    DESERT_RUINS,
    ASTRAL_VOID,
}

enum class DiceAppearanceMode {
    CHARACTER_DEFAULT,
    UNIFORM,
    PER_DIE,
    RANDOM_UNIFORM,
    RANDOM_PER_DIE,
}

data class DiceStyle(
    val id: String,
    val characterId: String,
    val name: String,
    val material: DiceMaterial = DiceMaterial.GLOSSY_RESIN,
    val primaryColorArgb: Int,
    val secondaryColorArgb: Int,
    val order: Int = 0,
)

data class RollDiceAppearance(
    val mode: DiceAppearanceMode = DiceAppearanceMode.CHARACTER_DEFAULT,
    val styleId: String? = null,
    val subgroupStyleIds: Map<String, String> = emptyMap(),
    val perDieStyleIds: Map<String, String> = emptyMap(),
    val randomStyleIds: List<String> = emptyList(),
)

data class CharacterImageRef(
    val assetId: String,
    val sha256: String,
    val mimeType: String,
    val byteSize: Long,
)

data class CharacterProfile(
    val id: String,
    val name: String,
    val imageUri: String? = null,
    val image: CharacterImageRef? = null,
    val tag: String = "",
    val level: Int = 1,
    val order: Int = 0,
    val defaultDiceStyleId: String? = null,
    val diceTableTheme: DiceTableTheme = DiceTableTheme.ARCANE,
    val diceTableImage: CharacterImageRef? = null,
)

data class CharacterModifier(
    val id: String,
    val characterId: String,
    val name: String,
    val value: Int,
    val order: Int = 0,
)

data class RollLevelRule(
    val id: String,
    val kind: LevelRuleKind,
    val trigger: Int,
    val expression: String,
)

data class RollSubgroup(
    val id: String,
    val name: String = "",
    val expression: String,
    val operator: RollSubgroupOperator = RollSubgroupOperator.ADD,
)

data class RollGroup(
    val id: String,
    val characterId: String,
    val name: String,
    val order: Int = 0,
)

data class RollDefinition(
    val id: String,
    val characterId: String,
    val name: String,
    val expression: String,
    val groupId: String? = null,
    val enabled: Boolean = true,
    val order: Int = 0,
    val levelRules: List<RollLevelRule> = emptyList(),
    val subgroups: List<RollSubgroup> = emptyList(),
    val diceAppearance: RollDiceAppearance = RollDiceAppearance(),
)

data class RollLog(
    val id: String,
    val characterId: String,
    val rollDefinitionId: String,
    val rollName: String,
    val expression: String,
    val total: Int,
    val detail: String,
    val timestamp: Long,
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val shakeEnabled: Boolean = true,
    val animationsEnabled: Boolean = true,
    val showRollButton: Boolean = true,
    val rollButtonPosition: RollButtonPosition = RollButtonPosition.BOTTOM_RIGHT,
    val logRetention: Int = 20,
    // Device-local by design: this preference never participates in roaming settings sync.
    val conflictPolicy: ConflictPolicy = ConflictPolicy.ASK,
)

data class CharacterSyncMetadata(
    val characterId: String,
    val updatedAt: Long,
    val revision: String,
    val writerId: String,
    val baseRevision: String? = null,
)

data class AppData(
    val characters: List<CharacterProfile> = emptyList(),
    val modifiers: List<CharacterModifier> = emptyList(),
    val groups: List<RollGroup> = emptyList(),
    val rolls: List<RollDefinition> = emptyList(),
    val logs: List<RollLog> = emptyList(),
    val diceStyles: List<DiceStyle> = emptyList(),
    val characterSyncMetadata: List<CharacterSyncMetadata> = emptyList(),
)
